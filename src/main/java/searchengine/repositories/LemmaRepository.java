package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;

import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    Optional<LemmaEntity> findBySiteIdAndLemma(Integer siteId, String lemma);

    @Modifying
    @Transactional
    @Query("UPDATE LemmaEntity l SET l.frequency = l.frequency - 1 WHERE l.site.id = :siteId AND l.lemma = :lemma")
    void decrementAllFrequencyBySiteIdAndLemma(@Param("siteId") Integer siteId, @Param("lemma") String lemma);

    int countAllBySiteId(Integer id);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO lemma (lemma, site_id, frequency)
        VALUES (:lemma, :siteId, 1)
        ON DUPLICATE KEY UPDATE frequency = frequency + 1
    """, nativeQuery = true)
    void upsertLemma(@Param("lemma") String lemma, @Param("siteId") int siteId);

    @Query("""
        SELECT MAX(l.frequency * 1.0) / (SELECT COUNT(p) FROM PageEntity p WHERE p.site.id = :siteId)
        FROM LemmaEntity l
        WHERE l.site.id = :siteId
    """)
    Double findMaxPercentageLemmaOnPagesBySiteId(@Param("siteId") int siteId);

    @Query("""
        SELECT (COUNT(DISTINCT ie.page.id) * 1.0) / (SELECT COUNT(p) FROM PageEntity p)
        FROM SearchIndexEntity ie
        WHERE ie.lemma.id = :lemmaId
    """)
    Double percentageLemmaOnPagesById(@Param("lemmaId") Integer lemmaId);
}

