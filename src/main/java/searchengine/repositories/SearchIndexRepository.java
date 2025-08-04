package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.SearchIndexEntity;
@Repository
public interface SearchIndexRepository extends JpaRepository<SearchIndexEntity,Integer> {
    void deleteAllByPageId(int pageId);
    @Query("""
    SELECT SUM(ie.lemmaRank)
    FROM SearchIndexEntity ie
    WHERE ie.page.id = :pageId
  """)
    Double absoluteRelevanceByPageId(@Param("pageId") int pageId);
}
