package com.cartracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Tiny key/value table holding process state that must survive restarts —
 * currently the watermark for the scheduled digest ("last sent at") so a
 * restart never re-sends or skips a digest.
 */
@Entity
@Table(name = "digest_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigestStateEntity {
  @Id
  @Column(name = "state_key", nullable = false)
  private String stateKey;

  @Column(name = "state_value", length = 64)
  private String stateValue;

  @Column(name = "updated_at")
  private Instant updatedAt;
}
