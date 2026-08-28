package ssafy.a507.backend.domain.community.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ssafy.a507.backend.domain.community.entity.PostLike;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {}
