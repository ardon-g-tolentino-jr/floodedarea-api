package com.humanworkstream.baha.service;

import com.humanworkstream.baha.dto.CreateFeedbackRequest;
import com.humanworkstream.baha.entity.AppUser;
import com.humanworkstream.baha.entity.Feedback;
import com.humanworkstream.baha.entity.FeedbackUpvote;
import com.humanworkstream.baha.enumeration.FeedbackStatus;
import com.humanworkstream.baha.enumeration.UserRole;
import com.humanworkstream.baha.repository.AppUserRepository;
import com.humanworkstream.baha.repository.FeedbackRepository;
import com.humanworkstream.baha.repository.FeedbackUpvoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository repository;
    private final FeedbackUpvoteRepository upvoteRepository;
    private final AppUserRepository userRepository;
    private final TurnstileVerifier turnstile;

    /** Accepted feedback (bugs + features) — the public board, ordered by upvotes desc.
     *  {@code viewerUserId} is the caller's id (or null when anonymous) for the per-item vote flag. */
    @Transactional(readOnly = true)
    public List<Feedback> acceptedFeedback(Integer viewerUserId) {
        List<Feedback> items = repository.findByStatusOrderByCreatedAtDesc(FeedbackStatus.accepted);
        decorate(items, viewerUserId);
        items.sort(Comparator.comparingInt(Feedback::getUpvotes).reversed()
                .thenComparing(Feedback::getCreatedAt, Comparator.reverseOrder()));
        return items;
    }

    /** Full moderation queue, newest first (admin). Decorated with upvote totals so admins can prioritize. */
    @Transactional(readOnly = true)
    public List<Feedback> findAll(Integer viewerUserId) {
        List<Feedback> items = repository.findAllByOrderByCreatedAtDesc();
        decorate(items, viewerUserId);
        return items;
    }

    @Transactional
    public Feedback create(CreateFeedbackRequest req, int userId, UserRole role, String clientIp) {
        // No-login guests must clear CAPTCHA when Turnstile is configured (no-op otherwise) — mirrors
        // guest flood reporting, the other "anyone can act" write.
        if (role == UserRole.GUEST && !turnstile.verify(req.captchaToken(), clientIp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not verify you are human. Please try again.");
        }
        Feedback f = new Feedback();
        f.setType(req.type());
        f.setTitle(req.title().trim());
        f.setDetails(req.details() != null && !req.details().isBlank() ? req.details().trim() : null);
        f.setStatus(FeedbackStatus.pending);
        f.setSubmittedByUserId(userId);
        f.setSubmitterName(userRepository.findById(userId).map(AppUser::getName).orElse(null));
        Feedback saved = repository.save(f);
        // Brand-new item: nothing can have upvoted it yet.
        saved.setUpvotes(0);
        saved.setUpvotedByMe(false);
        log.info("[feedback] created id={} type={} by userId={} role={}", saved.getId(), saved.getType(), userId, role);
        return saved;
    }

    @Transactional
    public Optional<Feedback> updateStatus(Integer id, FeedbackStatus status) {
        return repository.findById(id).map(f -> {
            f.setStatus(status);
            Feedback saved = repository.save(f);
            decorate(List.of(saved), null);
            return saved;
        });
    }

    /** Toggle the caller's upvote on an item (one per account/guest). Returns the decorated item,
     *  or empty when the item does not exist. */
    @Transactional
    public Optional<Feedback> toggleUpvote(Integer feedbackId, int userId) {
        return repository.findById(feedbackId).map(f -> {
            if (upvoteRepository.existsByFeedbackIdAndUserId(feedbackId, userId)) {
                upvoteRepository.deleteByFeedbackIdAndUserId(feedbackId, userId);
            } else {
                FeedbackUpvote u = new FeedbackUpvote();
                u.setFeedbackId(feedbackId);
                u.setUserId(userId);
                upvoteRepository.save(u);
            }
            f.setUpvotes((int) upvoteRepository.countByFeedbackId(feedbackId));
            f.setUpvotedByMe(upvoteRepository.existsByFeedbackIdAndUserId(feedbackId, userId));
            return f;
        });
    }

    /** Fill in {@code upvotes}/{@code upvotedByMe} for a list in two queries (totals + the viewer's votes). */
    private void decorate(List<Feedback> items, Integer viewerUserId) {
        if (items.isEmpty()) return;
        Map<Integer, Long> totals = upvoteRepository.countsGrouped().stream()
                .collect(Collectors.toMap(
                        FeedbackUpvoteRepository.UpvoteCount::getFeedbackId,
                        FeedbackUpvoteRepository.UpvoteCount::getTotal));
        Set<Integer> mine = viewerUserId == null
                ? Set.of()
                : new HashSet<>(upvoteRepository.feedbackIdsUpvotedBy(viewerUserId));
        for (Feedback f : items) {
            f.setUpvotes(totals.getOrDefault(f.getId(), 0L).intValue());
            f.setUpvotedByMe(mine.contains(f.getId()));
        }
    }
}
