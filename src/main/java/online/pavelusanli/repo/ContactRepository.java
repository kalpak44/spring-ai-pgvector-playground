package online.pavelusanli.repo;

import online.pavelusanli.model.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    @Query("""
        SELECT c FROM Contact c WHERE
            LOWER(COALESCE(c.firstName,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR
            LOWER(COALESCE(c.lastName,''))  LIKE LOWER(CONCAT('%',:q,'%')) OR
            COALESCE(c.phone,'')            LIKE       CONCAT('%',:q,'%')  OR
            LOWER(COALESCE(c.email,''))     LIKE LOWER(CONCAT('%',:q,'%'))
        """)
    Page<Contact> search(@Param("q") String query, Pageable pageable);

    @Query("""
        SELECT c FROM Contact c WHERE
            LOWER(COALESCE(c.firstName,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR
            LOWER(COALESCE(c.lastName,''))  LIKE LOWER(CONCAT('%',:q,'%')) OR
            COALESCE(c.phone,'')            LIKE       CONCAT('%',:q,'%')  OR
            LOWER(COALESCE(c.email,''))     LIKE LOWER(CONCAT('%',:q,'%'))
        """)
    List<Contact> searchSuggestions(@Param("q") String query, Pageable pageable);
}