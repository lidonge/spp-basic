package com.servpp.service.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servpp.service.jdbc.JdbcTransactionScope;
import com.servpp.service.runtime.AppDdlCatalog;
import com.servpp.service.runtime.RoleIdentityContext;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** APP-owned role identity links; performer business facts are never written here. */
public final class JdbcRoleRelationshipStore {
    private final DataSource source;
    private final AppDdlCatalog catalog;
    private final ObjectMapper mapper;

    public JdbcRoleRelationshipStore(DataSource source, AppDdlCatalog catalog, ObjectMapper mapper) {
        this.source = source; this.catalog = catalog; this.mapper = mapper;
    }

    public static String column(String performer) { return "spp_performer_" + performer; }

    public static void ensureTables(Connection connection, AppDdlCatalog catalog) throws SQLException {
        for (var role : catalog.roleStorage().values()) {
            if (role.performerTable().isBlank()) continue;
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table if not exists " + role.performerTable()
                        + " (role_type varchar(255) not null, role_id varchar(2048) not null,"
                        + " performer_type varchar(255) not null, performer_key clob not null,"
                        + " primary key(role_type, role_id, performer_type))");
            }
        }
    }

    public void save(String type, String identity, Object value) {
        var role = catalog.roleStorage().get(type);
        if (role == null) return;
        Connection connection = JdbcTransactionScope.currentConnection(source)
                .orElseThrow(() -> new IllegalStateException("ROLE_STORAGE_NOT_ENLISTED"));
        var actors = RoleIdentityContext.performers(value,type).orElse(List.of());
        Map<AppDdlCatalog.Performer,Map<String,Object>> links = new LinkedHashMap<>();
        for (var performer : role.performers()) {
            Object actor = performer.member().isBlank() ? null : ObjectPropertyAccess.read(value,performer.member());
            if (actor == null && performer.member().isBlank()) actor = actors.stream().filter(item -> item.getClass().getSimpleName().equals(performer.type())).findFirst().orElse(null);
            if (actor == null) continue;
            Map<String,Object> keys = new LinkedHashMap<>();
            RepositoryParticipantSnapshots.objectIdentity(actor,performer.type()).ifPresent(id -> keys.put("$identity",id));
            for (String path : performer.keys()) {
                if (path.startsWith("@")) continue;
                Object key = ObjectPropertyAccess.read(actor,path);
                if (key != null) putPath(keys,path,key);
            }
            if (keys.isEmpty()) continue;
            links.put(performer,keys);
        }
        var present = links.keySet().stream().map(AppDdlCatalog.Performer::slot).collect(java.util.stream.Collectors.toSet());
        if (!role.variants().isEmpty() && role.variants().stream().noneMatch(branch -> new HashSet<>(branch).equals(present)))
            throw new IllegalStateException("ROLE_PERFORMER_VARIANT_INVALID: " + type + " " + present);
        // A complete replacement prevents inactive alternatives from surviving a change of performer.
        if (!role.variants().isEmpty()) {
            if (!role.performerTable().isBlank()) delete(type,identity);
            else try (PreparedStatement clear = connection.prepareStatement("update " + role.table() + " set "
                    + role.performers().stream().map(actor -> column(actor.slot()) + "=null").collect(java.util.stream.Collectors.joining(","))
                    + " where " + role.primaryKey() + "=?")) {
                clear.setString(1,identity); clear.executeUpdate();
            } catch (SQLException failure) { throw new IllegalStateException("ROLE_RELATIONSHIP_WRITE_FAILED",failure); }
        }
        for (var link : links.entrySet()) {
            var performer = link.getKey(); var keys = link.getValue();
            try {
                String json = mapper.writeValueAsString(keys);
                if (role.performerTable().isBlank()) {
                    try (PreparedStatement statement = connection.prepareStatement("update " + role.table() + " set "
                            + column(performer.slot()) + "=? where " + role.primaryKey() + "=?")) {
                        statement.setString(1,json); statement.setString(2,identity);
                        if (statement.executeUpdate() != 1) throw new IllegalStateException("ROLE_STORAGE_ROW_NOT_FOUND");
                    }
                } else {
                    try (PreparedStatement delete = connection.prepareStatement("delete from " + role.performerTable()
                            + " where role_type=? and role_id=? and performer_type=?")) {
                        delete.setString(1,type); delete.setString(2,identity); delete.setString(3,performer.slot()); delete.executeUpdate();
                    }
                    try (PreparedStatement insert = connection.prepareStatement("insert into " + role.performerTable()
                            + "(role_type,role_id,performer_type,performer_key) values (?,?,?,?)")) {
                        insert.setString(1,type); insert.setString(2,identity); insert.setString(3,performer.slot()); insert.setString(4,json); insert.executeUpdate();
                    }
                }
            } catch (Exception failure) { throw new IllegalStateException("ROLE_RELATIONSHIP_WRITE_FAILED",failure); }
        }
    }

    public void load(String type, String identity, Object target) {
        var role = catalog.roleStorage().get(type);
        if (role == null) return;
        try {
            JdbcTransactionScope.execute(source,connection -> {
                List<Object> actors = new ArrayList<>();
                for (var performer : role.performers()) {
                    String sql = role.performerTable().isBlank()
                            ? "select " + column(performer.slot()) + " from " + role.table() + " where " + role.primaryKey() + "=?"
                            : "select performer_key from " + role.performerTable() + " where role_type=? and role_id=? and performer_type=?";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        if (role.performerTable().isBlank()) statement.setString(1,identity);
                        else { statement.setString(1,type); statement.setString(2,identity); statement.setString(3,performer.slot()); }
                        try (ResultSet rows = statement.executeQuery()) {
                            if (rows.next() && rows.getString(1) != null) actors.add(attach(target,role,performer,rows.getString(1)));
                        }
                    }
                }
                RoleIdentityContext.attachPerformers(target,role.type(),actors.toArray());
                return null;
            });
        } catch (SQLException failure) { throw new IllegalStateException("ROLE_RELATIONSHIP_READ_FAILED",failure); }
    }

    private Object attach(Object target, AppDdlCatalog.RoleStorage role, AppDdlCatalog.Performer performer, String json) {
        try {
            Map<String,Object> facts = mapper.readerFor(new TypeReference<Map<String,Object>>() { })
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(json);
            String identity = (String)facts.remove("$identity");
            Class<?> type = Class.forName(performer.javaType(),true,Thread.currentThread().getContextClassLoader());
            Object actor = performer.member().isBlank() ? null : ObjectPropertyAccess.read(target,performer.member());
            if (actor == null) actor = type.getConstructor().newInstance();
            mapper.readerForUpdating(actor).readValue(mapper.writeValueAsString(facts));
            if (identity != null) RepositoryParticipantSnapshots.attachIdentity(actor,performer.type(),identity);
            if (!performer.member().isBlank()) {
                String member = performer.member();
                target.getClass().getMethod("set" + Character.toUpperCase(member.charAt(0)) + member.substring(1),type).invoke(target,actor);
            }
            return actor;
        } catch (ReflectiveOperationException | java.io.IOException failure) {
            throw new IllegalStateException("ROLE_RELATIONSHIP_HYDRATION_FAILED",failure);
        }
    }

    public void delete(String type, String identity) {
        var role = catalog.roleStorage().get(type);
        if (role == null || role.performerTable().isBlank()) return;
        Connection connection = JdbcTransactionScope.currentConnection(source)
                .orElseThrow(() -> new IllegalStateException("ROLE_STORAGE_NOT_ENLISTED"));
        try (PreparedStatement statement = connection.prepareStatement("delete from " + role.performerTable() + " where role_type=? and role_id=?")) {
            statement.setString(1,type); statement.setString(2,identity); statement.executeUpdate();
        } catch (SQLException failure) { throw new IllegalStateException("ROLE_RELATIONSHIP_DELETE_FAILED",failure); }
    }

    private static void putPath(Map<String,Object> target, String path, Object value) {
        String[] names = path.split("\\."); Map<String,Object> current = target;
        for (int i=0;i<names.length-1;i++) current = (Map<String,Object>)current.computeIfAbsent(names[i],ignored -> new LinkedHashMap<>());
        current.put(names[names.length-1],value);
    }
}
