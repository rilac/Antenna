package ssafy.a507.backend.domain.community.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.community.entity.FeedPost;

public interface FeedPostRepository extends JpaRepository<FeedPost, Long> {}
