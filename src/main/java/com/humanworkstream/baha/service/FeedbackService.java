package com.humanworkstream.baha.service;

import com.humanworkstream.baha.dto.CreateFeedbackRequest;
import com.humanworkstream.baha.entity.AppUser;
import com.humanworkstream.baha.entity.Feedback;
import com.humanworkstream.baha.enumeration.FeedbackStatus;
import com.humanworkstream.baha.enumeration.FeedbackType;
import com.humanworkstream.baha.repository.AppUserRepository;
import com.humanworkstream.baha.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository repository;
    private final AppUserRepository userRepository;

    /** Accepted feature requests — the public list shown to every registered user. */
    @Transactional(readOnly = true)
    public List<Feedback> acceptedFeatures() {
        return repository.findByTypeAndStatusOrderByCreatedAtDesc(FeedbackType.feature, FeedbackStatus.accepted);
    }

    /** Full moderation queue, newest first (admin). */
    @Transactional(readOnly = true)
    public List<Feedback> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Feedback create(CreateFeedbackRequest req, int userId) {
        Feedback f = new Feedback();
        f.setType(req.type());
        f.setTitle(req.title().trim());
        f.setDetails(req.details() != null && !req.details().isBlank() ? req.details().trim() : null);
        f.setStatus(FeedbackStatus.pending);
        f.setSubmittedByUserId(userId);
        f.setSubmitterName(userRepository.findById(userId).map(AppUser::getName).orElse(null));
        Feedback saved = repository.save(f);
        log.info("[feedback] created id={} type={} by userId={}", saved.getId(), saved.getType(), userId);
        return saved;
    }

    @Transactional
    public Optional<Feedback> updateStatus(Integer id, FeedbackStatus status) {
        return repository.findById(id).map(f -> {
            f.setStatus(status);
            return repository.save(f);
        });
    }
}
