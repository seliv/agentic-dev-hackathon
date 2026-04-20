import { useState, useEffect, useCallback, useRef } from 'react';
import type { ChatRoom, ChatMessage, MessageEvent } from '../api/types.ts';
import { roomsApi } from '../api/rooms.ts';
import { presenceApi } from '../api/presence.ts';
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
import { ManageRoomModal } from '../components/ManageRoomModal.tsx';

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
  const [replyTo, setReplyTo] = useState<ChatMessage | null>(null);
  const [editingMessage, setEditingMessage] = useState<ChatMessage | null>(null);
  const [manageOpen, setManageOpen] = useState(false);
  const [currentUserRole, setCurrentUserRole] = useState<string | undefined>(undefined);

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

  const handleNotification = useCallback((notification: { type: string; data: Record<string, string> }) => {
    if (notification.type === 'ROOM_BANNED') {
      const { roomId } = notification.data;
      setRooms(prev => prev.filter(r => r.id !== roomId));
      if (selectedRoomRef.current?.id === roomId) {
        setSelectedRoom(null);
      }
    }
  }, []);

  const handleEvent = useCallback((roomId: string, event: MessageEvent) => {
    if (event.type === 'MESSAGE_EDITED') {
      const updated = event.data as ChatMessage;
      setMessages(prev => {
        const next = new Map(prev);
        const existing = next.get(roomId) || [];
        next.set(roomId, existing.map(m => m.id === updated.id ? updated : m));
        return next;
      });
    } else if (event.type === 'MESSAGE_DELETED') {
      const { messageId } = event.data as { messageId: string };
      setMessages(prev => {
        const next = new Map(prev);
        const existing = next.get(roomId) || [];
        next.set(roomId, existing.map(m =>
          m.id === messageId ? { ...m, deleted: true, content: null, attachments: [] } : m
        ));
        return next;
      });
    } else if (event.type === 'ROOM_DELETED') {
      setRooms(prev => prev.filter(r => r.id !== roomId));
      if (selectedRoomRef.current?.id === roomId) {
        setSelectedRoom(null);
      }
    } else if (event.type === 'MEMBER_ROLE_CHANGED') {
      const { userId, newRole } = event.data as { userId: number; username: string; newRole: string };
      if (userId === user!.id && selectedRoomRef.current?.id === roomId) {
        setCurrentUserRole(newRole);
      }
    } else if (event.type === 'MEMBER_REMOVED') {
      const { userId } = event.data as { userId: number };
      if (userId === user!.id) {
        setRooms(prev => prev.filter(r => r.id !== roomId));
        if (selectedRoomRef.current?.id === roomId) {
          setSelectedRoom(null);
        }
      }
    }
  }, [user]);

  const { subscribe, unsubscribe, sendMessage } = useWebSocket({
    onMessage: handleNewMessage,
    onPresence: handlePresenceEvent,
    onEvent: handleEvent,
    onNotification: handleNotification,
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
    setReplyTo(null);
    setEditingMessage(null);

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

    presenceApi.getRoomMemberPresence(room.id)
      .then(presences => presences.forEach(handlePresenceEvent))
      .catch(console.error);

    // Fetch current user's role in this room
    try {
      const roomMembers = await roomsApi.getMembers(room.id);
      const me = roomMembers.find(m => m.userId === user!.id);
      setCurrentUserRole(me?.role);
    } catch (err) {
      console.error('Failed to load member role', err);
    }
  }, [selectedRoom, subscribe, unsubscribe, markAsRead, handlePresenceEvent, user]);

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

  const handleSendMessage = useCallback(async (content: string, files: File[], replyToId?: string) => {
    if (!selectedRoom) return;

    if (editingMessage) {
      try {
        await roomsApi.editMessage(selectedRoom.id, editingMessage.id, content);
      } catch (err) {
        console.error('Failed to edit message', err);
      }
      setEditingMessage(null);
      return;
    }

    try {
      if (files.length > 0) {
        const msg = await roomsApi.sendMessageWithAttachments(selectedRoom.id, content || null, files, replyToId);
        handleNewMessage(selectedRoom.id, msg);
      } else if (replyToId) {
        const msg = await roomsApi.sendMessage(selectedRoom.id, content, replyToId);
        handleNewMessage(selectedRoom.id, msg);
      } else {
        sendMessage(selectedRoom.id, content);
      }
    } catch (err) {
      console.error('Failed to send message', err);
    }

    setReplyTo(null);
  }, [selectedRoom, editingMessage, sendMessage, handleNewMessage]);

  const handleDeleteMessage = useCallback(async (messageId: string) => {
    if (!selectedRoom) return;
    try {
      await roomsApi.deleteMessage(selectedRoom.id, messageId);
    } catch (err) {
      console.error('Failed to delete message', err);
    }
  }, [selectedRoom]);

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

  const handleFileDrop = useCallback((_files: File[]) => {
    // Files from drag-drop are handled by MessageInput's addFiles
    // For now, this is a placeholder — the drag-drop UI is in ChatArea
    // but the actual file state lives in MessageInput
  }, []);

  const getDmPresence = () => {
    if (!selectedRoom || selectedRoom.type !== 'DIRECT') return undefined;
    const dmMatch = selectedRoom.name.match(/^dm-(\d+)-(\d+)$/);
    if (!dmMatch) return undefined;
    const id1 = Number(dmMatch[1]);
    const id2 = Number(dmMatch[2]);
    const otherUserId = id1 === user!.id ? id2 : id1;
    return getPresence(otherUserId);
  };

  const handleRoomUpdated = useCallback((updatedRoom: ChatRoom) => {
    setRooms(prev => prev.map(r => r.id === updatedRoom.id ? updatedRoom : r));
    if (selectedRoom?.id === updatedRoom.id) {
      setSelectedRoom(updatedRoom);
    }
  }, [selectedRoom]);

  const handleRoomDeleted = useCallback(() => {
    if (selectedRoom) {
      unsubscribe(selectedRoom.id);
      setRooms(prev => prev.filter(r => r.id !== selectedRoom.id));
      setSelectedRoom(null);
    }
    setManageOpen(false);
  }, [selectedRoom, unsubscribe]);

  const currentMessages = selectedRoom ? (messages.get(selectedRoom.id) || []) : [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <AppHeader />
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          {selectedRoom ? (
            <>
              <RoomHeader
                room={selectedRoom}
                currentUserId={user!.id}
                currentUserRole={currentUserRole}
                onLeave={handleLeaveRoom}
                onInvite={selectedRoom.type === 'PRIVATE' ? () => setInviteOpen(true) : undefined}
                onManage={() => setManageOpen(true)}
                dmPresence={getDmPresence()}
              />
              <ChatArea
                messages={currentMessages}
                currentUserId={user!.id}
                roomOwnerId={selectedRoom.ownerId}
                isRoomAdmin={currentUserRole === 'ADMIN' || currentUserRole === 'OWNER'}
                onLoadMore={handleLoadMore}
                loading={loadingMessages}
                onReply={setReplyTo}
                onEdit={setEditingMessage}
                onDelete={handleDeleteMessage}
                onFileDrop={handleFileDrop}
              />
              <MessageInput
                onSend={handleSendMessage}
                replyTo={replyTo}
                onCancelReply={() => setReplyTo(null)}
                editingMessage={editingMessage}
                onCancelEdit={() => setEditingMessage(null)}
              />
            </>
          ) : (
            <div style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', color: '#999' }}>
              Select a room to start chatting
            </div>
          )}
        </div>
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
      {selectedRoom && (
        <ManageRoomModal
          open={manageOpen}
          room={selectedRoom}
          currentUserId={user!.id}
          onClose={() => setManageOpen(false)}
          onRoomUpdated={handleRoomUpdated}
          onRoomDeleted={handleRoomDeleted}
        />
      )}
    </div>
  );
}
