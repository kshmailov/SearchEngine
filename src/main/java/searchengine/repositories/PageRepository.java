package searchengine.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity,Integer> {
    @Query("SELECT p FROM PageEntity p WHERE p.path = :path AND p.site.id = :siteId")
    Optional<PageEntity> findByPathAndSiteId(String path, int siteId);

    @Modifying
    @Transactional
    @Query(value = """
      INSERT INTO page (path, site_id, code, content)
      VALUES (:path, :siteId, :code, :content)
      ON DUPLICATE KEY UPDATE
        code = VALUES(code),
        content = VALUES(content)
      """, nativeQuery = true)
    void upsertPage(@Param("path") String path,
                    @Param("siteId") Integer siteId,
                    @Param("code") int code,
                    @Param("content") String content);

    int countAllBySiteId(Integer id);
    @Query("""
      SELECT DISTINCT p
      FROM PageEntity p
      JOIN SearchIndexEntity s ON s.page = p
      WHERE s.lemma.id = :lemmaId
  """)
    Page<PageEntity> findAllByLemmaId(
            @Param("lemmaId") Integer lemmaId,
            Pageable pageable
    );
}
