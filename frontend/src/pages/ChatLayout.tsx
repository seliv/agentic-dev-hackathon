import { useState, useEffect, useCallback, useRef } from 'react';
import type { ChatRoom, ChatMessage } from '../api/types.ts';
import { roomsApi } from '../api/rooms.ts';
import { useAuth } from '../contexts/AuthContext.tsx';
import { useWebSocket } from '../hooks/useWebSocket.ts';
import { usePresence } from '../hooks/usePresence.ts';
import { useUnread } from '../hooks/useUnread.ts';
import { AppHeader } from '../components/AppHeader.tsx';
import { RoomList } from '../components/RoomList.tsx';
import { RoomBrowser } from '../components/RoomBrowser.tsx';
import { CreateRoomModal } from '../components/CreateRoomModal.tsx';
import { ChatArea } from '../components/ChatArea.tsx';
import { MessageInput } from '../components/MessageInput.tsx';
import { RoomHeader } from '../components/RoomHeader.tsx';
import { ContactsPanel } from '../components/ContactsPanel.tsx';
import { UserSearchModal } from '../components/UserSearchModal.tsx';
import { InviteToRoomModal } from '../components/InviteToRoomModal.tsx';

export function ChatLayout() {
  const { user } = useAuth();
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [selectedRoom, setSelectedRoom] = useState<ChatRoom | null>(null);
  const [messages, setMessages] = useState<Map<string, ChatMessage[]>>(new Map());
  const [browserOpen, setBrowserOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [contactsOpen, setContactsOpen] = useState(false);
  const [userSearchOpen, setUserSearchOpen] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);

  const { getPresence, handlePresenceEvent } = usePresence();
  const { getUnreadCount, incrementUnread, markAsRead } = useUnread();

  const selectedRoomRef = useRef<ChatRoom | null>(null);
  useEffect(() => { selectedRoomRef.current = selectedRoom; }, [selectedRoom]);

  const handleNewMessage = useCallback((roomId: string, msg: ChatMessage) => {
    setMessages(prev => {
      const next = new Map(prev);
      const existing = next.get(roomId) || [];
      if (existing.some(m => m.id === msg.id)) return prev;
      next.set(roomId, [...existing, msg]);
      return next;
    });

    if (!selectedRoomRef.current || selectedRoomRef.current.id !== roomId) {
      incrementUnread(roomId);
    }
  }, [incrementUnread]);

  const { subscribe, unsubscribe, sendMessage } = useWebSocket({
    onMessage: handleNewMessage,
    onPresence: handlePresenceEvent,
  });

  useEffect(() => {
    roomsApi.getMyRooms().then(setRooms).catch(console.error);
  }, []);

  const handleSelectRoom = useCallback(async (room: ChatRoom) => {
    if (selectedRoom) {
      unsubscribe(selectedRoom.id);
    }
    setLoadingMessages(true);
    setSelectedRoom(room);

    try {
      const msgs = await roomsApi.getMessages(room.id);
      if (msgs.length > 0) {
        markAsRead(room.id, msgs[0].id);
      }
      setMessages(prev => {
        const next = new Map(prev);
        next.set(room.id, msgs.reverse());
        return next;
      });
    } catch (err) {
      console.error('Failed to load messages', err);
    } finally {
      setLoadingMessages(false);
    }

    subscribe(room.id);
  }, [selectedRoom, subscribe, unsubscribe, markAsRead]);

  const handleLoadMore = useCallback(async (): Promise<boolean> => {
    if (!selectedRoom) return false;
    const currentMessages = messages.get(selectedRoom.id) || [];
    const oldest = currentMessages[0];
    if (!oldest) return false;

    const olderMessages = await roomsApi.getMessages(selectedRoom.id, oldest.createdAt);
    if (olderMessages.length === 0) return false;

    setMessages(prev => {
      const next = new Map(prev);
      const existing = next.get(selectedRoom.id) || [];
      next.set(selectedRoom.id, [...olderMessages.reverse(), ...existing]);
      return next;
    });

    return olderMessages.length >= 50;
  }, [selectedRoom, messages]);

  const handleSendMessage = useCallback(async (content: string) => {
    if (!selectedRoom) return;
    try {
      const msg = await roomsApi.sendMessage(selectedRoom.id, content);
      handleNewMessage(selectedRoom.id, msg);
    } catch {
      sendMessage(selectedRoom.id, content);
    }
  }, [selectedRoom, sendMessage, handleNewMessage]);

  const handleLeaveRoom = useCallback(async () => {
    if (!selectedRoom) return;
    await roomsApi.leaveRoom(selectedRoom.id);
    unsubscribe(selectedRoom.id);
    setRooms(prev => prev.filter(r => r.id !== selectedRoom.id));
    setSelectedRoom(null);
  }, [selectedRoom, unsubscribe]);

  const handleRoomCreated = useCallback((room: ChatRoom) => {
    setRooms(prev => [...prev, room]);
    setCreateOpen(false);
    handleSelectRoom(room);
  }, [handleSelectRoom]);

  const handleRoomJoined = useCallback((room: ChatRoom) => {
    setRooms(prev => prev.some(r => r.id === room.id) ? prev : [...prev, room]);
    setBrowserOpen(false);
    handleSelectRoom(room);
  }, [handleSelectRoom]);

  const handleDMCreated = useCallback((room: ChatRoom) => {
    setRooms(prev => prev.some(r => r.id === room.id) ? prev : [...prev, room]);
    handleSelectRoom(room);
  }, [handleSelectRoom]);

  const getDmPresence = () => {
    if (!selectedRoom || selectedRoom.type !== 'DIRECT') return undefined;
    const dmMatch = selectedRoom.name.match(/^dm-(\d+)-(\d+)$/);
    if (!dmMatch) return undefined;
    const id1 = Number(dmMatch[1]);
    const id2 = Number(dmMatch[2]);
    const otherUserId = id1 === user!.id ? id2 : id1;
    return getPresence(otherUserId);
  };

  const currentMessages = selectedRoom ? (messages.get(selectedRoom.id) || []) : [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <AppHeader />
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* Chat Area - center */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          {selectedRoom ? (
            <>
              <RoomHeader
                room={selectedRoom}
                currentUserId={user!.id}
                onLeave={handleLeaveRoom}
                onInvite={selectedRoom.type === 'PRIVATE' ? () => setInviteOpen(true) : undefined}
                dmPresence={getDmPresence()}
              />
              <ChatArea messages={currentMessages} currentUserId={user!.id} onLoadMore={handleLoadMore} loading={loadingMessages} />
              <MessageInput onSend={handleSendMessage} />
            </>
          ) : (
            <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', color: '#999' }}>
              Select a room to start chatting
            </div>
          )}
        </div>
        {/* Right Sidebar */}
        <div style={{ width: 280, borderLeft: '1px solid #f0f0f0', backgroundColor: '#fafafa' }}>
          <RoomList
            rooms={rooms}
            selectedRoomId={selectedRoom?.id || null}
            onSelectRoom={handleSelectRoom}
            onBrowse={() => setBrowserOpen(true)}
            onCreate={() => setCreateOpen(true)}
            onContacts={() => setContactsOpen(true)}
            onUserSearch={() => setUserSearchOpen(true)}
            getUnreadCount={getUnreadCount}
            getPresence={getPresence}
          />
        </div>
      </div>
      <RoomBrowser open={browserOpen} onClose={() => setBrowserOpen(false)} onJoined={handleRoomJoined} joinedRoomIds={new Set(rooms.map(r => r.id))} />
      <CreateRoomModal open={createOpen} onClose={() => setCreateOpen(false)} onCreated={handleRoomCreated} />
      <ContactsPanel open={contactsOpen} onClose={() => setContactsOpen(false)} onDMCreated={handleDMCreated} />
      <UserSearchModal open={userSearchOpen} onClose={() => setUserSearchOpen(false)} onDMCreated={handleDMCreated} />
      {selectedRoom && (
        <InviteToRoomModal open={inviteOpen} roomId={selectedRoom.id} onClose={() => setInviteOpen(false)} />
      )}
    </div>
  );
}
