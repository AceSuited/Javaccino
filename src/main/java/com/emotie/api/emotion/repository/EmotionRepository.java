package com.emotie.api.emotion.repository;

import com.emotie.api.diaries.domain.Diaries;
import com.emotie.api.emotion.domain.Emotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmotionRepository  extends JpaRepository<Emotion, Integer> {
}
