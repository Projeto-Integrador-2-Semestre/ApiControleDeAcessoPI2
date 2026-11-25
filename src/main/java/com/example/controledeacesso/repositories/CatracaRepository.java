package com.example.controledeacesso.repositories;

import com.example.controledeacesso.db.D1Client;
import com.example.controledeacesso.entity.Catraca;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CatracaRepository {

    private final D1Client d1;

    public CatracaRepository(D1Client d1) {
        this.d1 = d1;
    }

    public List<Catraca> findAll() {
        return d1.query("SELECT id, nome, local FROM catraca ORDER BY id").stream()
                .map(CatracaRepository::mapear).toList();
    }

    public Optional<Catraca> findById(Long id) {
        return d1.query("SELECT id, nome, local FROM catraca WHERE id = ?", id).stream()
                .findFirst().map(CatracaRepository::mapear);
    }

    public Catraca save(String nome, String local) {
        Map<String, Object> row = d1.query(
                "INSERT INTO catraca (nome, local) VALUES (?, ?) RETURNING id, nome, local", nome, local).get(0);
        return mapear(row);
    }

    public void deleteById(Long id) {
        d1.update("DELETE FROM catraca WHERE id = ?", id);
    }

    private static Catraca mapear(Map<String, Object> row) {
        return new Catraca(((Number) row.get("id")).longValue(), (String) row.get("nome"), (String) row.get("local"));
    }
}
