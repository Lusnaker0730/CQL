# Flyway Rollback Scripts

Emergency rollback SQL scripts for each forward migration (`V1`~`V40`).

## Important Notes

- These scripts are **NOT** managed by Flyway. They must be executed **manually** against the production database.
- Flyway Community Edition does not support `undo` (`U__` prefix). These scripts live outside Flyway's control.
- After manual rollback, you **must** also delete the corresponding row from `flyway_schema_history`:
  ```sql
  DELETE FROM flyway_schema_history WHERE version = 'NN';
  ```
- Always take a **DB snapshot** before running any rollback script.
- Execute rollback scripts in **reverse order** (e.g., rollback V40 before V39).
- Some rollback scripts (e.g., V30, V34 seed data) are **lossy** — they delete inserted data but cannot restore previously deleted data.

## Naming Convention

`rollback_VNN__description.sql` — matches the corresponding forward migration.

## Usage

```bash
# 1. Take snapshot
pg_dump -h $HOST -U $USER -d $DB -F c -f backup_before_rollback.dump

# 2. Execute rollback (example: rolling back V40)
psql -h $HOST -U $USER -d $DB -f rollback_V40__ecqm_external_cql_library.sql

# 3. Remove Flyway history entry
psql -h $HOST -U $USER -d $DB -c "DELETE FROM flyway_schema_history WHERE version = '40';"

# 4. Verify
psql -h $HOST -U $USER -d $DB -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```
