package com.chatapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "read_receipts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "room_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class ReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id", nullable = false)
    private Message lastReadMessage;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;

}
