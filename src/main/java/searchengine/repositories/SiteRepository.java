package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;
import searchengine.model.Status;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity,Integer> {
    @Modifying
    @Transactional
    @Query("DELETE FROM SiteEntity  p WHERE p.url = :url")
    void deleteByUrl(@Param("url") String url);
    boolean existsByStatus(Status status);
    List<SiteEntity> findByStatus(Status status);

    boolean existsByUrl(String url);
    SiteEntity findByUrl(String url);

    Optional<List<SiteEntity>> findAllByStatus(Status status);
}
