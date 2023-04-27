package ru.job4j.accidents.repository;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import ru.job4j.accidents.model.Accident;
import ru.job4j.accidents.model.Rule;
import ru.job4j.accidents.util.AccidentRowMapper;

import java.sql.PreparedStatement;
import java.util.*;

@Repository
@AllArgsConstructor
public class AccidentJdbcTemplateRepository implements AccidentRepository {
    private final JdbcTemplate jdbc;


    /**
     * Сохранение модели Accident.
     * KeyHolder возвращает сгенерированный ID accident
     * <a href=docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#jdbc-auto-generated-keys>docs.spring.io</a>
     * 3.3.7. Retrieving Auto-generated Keys
     * An update() convenience method supports the retrieval of primary keys generated by the database.
     * This support is part of the JDBC 3.0 standard. See Chapter 13.6 of the specification for details.
     * The method takes a PreparedStatementCreator as its first argument,
     * and this is the way the required insert statement is specified.
     * The other argument is a KeyHolder,
     * which contains the generated key on successful return from the update.
     * There is no standard single way to create an appropriate PreparedStatement (which explains why the method signature is the way it is).
     * The following example works on Oracle but may not work on other platforms:
     * JavaKotlin
     * final String INSERT_SQL = "insert into my_test (name) values(?)";
     * final String name = "Rob";
     * KeyHolder keyHolder = new GeneratedKeyHolder();
     * jdbcTemplate.update(connection -> {
     * PreparedStatement ps = connection.prepareStatement(INSERT_SQL, new String[] { "id" });
     * ps.setString(1, name);
     * return ps;
     * }, keyHolder);
     * keyHolder.getKey() now contains the generated key
     *
     * @param accident Accident
     * @return Accident ID > 0;
     */

    @Override
    public Optional<Accident> save(Accident accident) {
        KeyHolder key = new GeneratedKeyHolder();
        final String INSERT_SQL = "INSERT INTO accidents(name, text, address, type_id) VALUES (?, ?, ?, ?)";
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, new String[]{"id"});
            ps.setString(1, accident.getName());
            ps.setString(2, accident.getText());
            ps.setString(3, accident.getAddress());
            ps.setInt(4, accident.getType().getId());
            return ps;
        }, key);
        accident.setId(key.getKey().intValue());
        saveAccidentsRules(accident);
        return Optional.of(accident);
    }

    @Override
    public Optional<Accident> findById(int accidentId) {
        Map<Integer, Accident> accidentMap = new HashMap<>();
        jdbc.query("SELECT * FROM accidents AS ac "
                        + "LEFT JOIN accident_types AS at ON ac.type_id = at.id "
                        + "LEFT JOIN accidents_rules AS ar ON ac.id = ar.accident_id "
                        + "LEFT JOIN accident_rules AS r ON ar.rule_id = r.id "
                        + "WHERE ac.id = ?",
                new AccidentRowMapper(accidentMap), accidentId);
        return Optional.ofNullable(accidentMap.get(accidentId));
    }

    @Override
    public boolean update(Accident accident) {
        int result = jdbc.update("UPDATE accidents "
                        + "SET name = ?, text = ?, address = ?, type_id = ? "
                        + "WHERE id = ?",
                accident.getName(), accident.getText(), accident.getAddress(),
                accident.getType().getId(), accident.getId());
        if (result > 0) {
            deleteAccidentRules(accident.getId());
            saveAccidentsRules(accident);
        }
        return result > 0;
    }

    @Override
    public boolean deleteById(int accidentId) {
        deleteAccidentRules(accidentId);
        int result = jdbc.update("DELETE FROM accidents AS ac WHERE ac.id = ?", accidentId);
        return result > 0;
    }

    @Override
    public List<Accident> findAll() {
        Map<Integer, Accident> accidentMap = new HashMap<>();
        jdbc.query(
                "SELECT * FROM accidents AS ac "
                        + "LEFT JOIN accident_types AS at ON ac.type_id = at.id "
                        + "LEFT JOIN accidents_rules AS ar ON ac.id = ar.accident_id "
                        + "LEFT JOIN accident_rules AS r ON ar.rule_id = r.id",
                new AccidentRowMapper(accidentMap)
        );
        return accidentMap.values().stream().toList();
    }

    /**
     * Сохранение в таблицу ACCIDENTS_RULES данных по статьям присвоенным ACCIDENT
     *
     * @param accident Accident
     */
    private void saveAccidentsRules(Accident accident) {
        for (Rule rule : accident.getRules()) {
            jdbc.update("INSERT INTO accidents_rules(accident_id, rule_id) VALUES (?, ?)",
                    accident.getId(), rule.getId());
        }
    }

    /**
     * Удаление из таблицы Accidents_Rules записи по Accident ID
     *
     * @param accidentId Accident ID
     */
    private void deleteAccidentRules(int accidentId) {
        jdbc.update("DELETE FROM accidents_rules AS ar WHERE ar.accident_id = ?", accidentId);
    }
}