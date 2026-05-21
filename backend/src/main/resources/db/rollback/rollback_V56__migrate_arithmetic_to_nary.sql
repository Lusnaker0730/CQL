-- Rollback for V56__migrate_arithmetic_to_nary
-- Converts arithmeticExpression base elements from N-ary shape (operands[]/operators[])
-- back to PAT-161 2-ary shape (left_*/right_*/operator scalar fields).
--
-- LIMITATION: only N=2 case is fully reversible. For N>2 we keep operands[0]
-- and operands[1] + operators[0]; the rest are dropped with a NOTICE.
-- This matches the design choice in the V56 migration risk analysis (issue #510).
--
-- This file is NOT auto-applied by Flyway. Run manually if production rollback
-- is needed:
--   psql -f rollback_V56__migrate_arithmetic_to_nary.sql cqlplatform
-- Then remove the V56 row from flyway_schema_history:
--   DELETE FROM flyway_schema_history WHERE version = '56';

DO $$
DECLARE
    rec RECORD;
    elements_jsonb jsonb;
    new_elements jsonb;
    i int;
    element jsonb;
    fields_arr jsonb;
    operands_field jsonb;
    operators_field jsonb;
    operands_arr jsonb;
    operators_arr jsonb;
    operand0 jsonb;
    operand1 jsonb;
    new_fields jsonb;
    n int;
BEGIN
    -- Process both tables
    FOR rec IN
        SELECT 'cds_artifact' AS tbl, id, base_elements FROM cds_artifact
            WHERE base_elements IS NOT NULL AND base_elements <> ''
        UNION ALL
        SELECT 'ecqm_artifact' AS tbl, id, base_elements FROM ecqm_artifact
            WHERE base_elements IS NOT NULL AND base_elements <> ''
    LOOP
        BEGIN
            elements_jsonb := rec.base_elements::jsonb;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'rollback_V56: skipping % id=% (invalid JSON)', rec.tbl, rec.id;
            CONTINUE;
        END;

        IF jsonb_typeof(elements_jsonb) <> 'array' THEN
            CONTINUE;
        END IF;

        new_elements := '[]'::jsonb;
        FOR i IN 0..jsonb_array_length(elements_jsonb) - 1 LOOP
            element := elements_jsonb -> i;
            -- Only transform arithmeticExpression elements; pass others through.
            IF element ->> 'type' <> 'arithmeticExpression' THEN
                new_elements := new_elements || jsonb_build_array(element);
                CONTINUE;
            END IF;
            fields_arr := element -> 'fields';
            IF fields_arr IS NULL OR jsonb_typeof(fields_arr) <> 'array' THEN
                new_elements := new_elements || jsonb_build_array(element);
                CONTINUE;
            END IF;
            -- Find operands + operators
            operands_field := NULL;
            operators_field := NULL;
            FOR n IN 0..jsonb_array_length(fields_arr) - 1 LOOP
                IF (fields_arr -> n ->> 'id') = 'operands' THEN
                    operands_field := fields_arr -> n -> 'value';
                ELSIF (fields_arr -> n ->> 'id') = 'operators' THEN
                    operators_field := fields_arr -> n -> 'value';
                END IF;
            END LOOP;
            IF operands_field IS NULL OR operators_field IS NULL THEN
                -- Already legacy shape (no operands/operators field) — leave alone.
                new_elements := new_elements || jsonb_build_array(element);
                CONTINUE;
            END IF;
            operands_arr := operands_field;
            operators_arr := operators_field;
            IF jsonb_array_length(operands_arr) > 2 THEN
                RAISE NOTICE 'rollback_V56: % id=% has N=% operands; rollback keeps only first 2 (data loss)',
                    rec.tbl, rec.id, jsonb_array_length(operands_arr);
            END IF;
            operand0 := operands_arr -> 0;
            operand1 := operands_arr -> 1;
            -- Build legacy fields[] from operand0, operand1, operators[0]
            new_fields := jsonb_build_array(
                jsonb_build_object('id', 'left_mode',          'type', 'string', 'name', 'Left Mode',
                                   'value', COALESCE(operand0 ->> 'mode', 'element')),
                jsonb_build_object('id', 'left_operand_id',    'type', 'string', 'name', 'Left Operand',
                                   'value', COALESCE(operand0 ->> 'operand_id', '')),
                jsonb_build_object('id', 'left_literal',       'type', 'string', 'name', 'Left Literal',
                                   'value', COALESCE(operand0 ->> 'operand_literal', '')),
                jsonb_build_object('id', 'left_literal_value', 'type', 'string', 'name', 'Left Literal Value',
                                   'value', COALESCE(operand0 ->> 'operand_literal_value', '')),
                jsonb_build_object('id', 'left_literal_unit',  'type', 'string', 'name', 'Left Literal Unit',
                                   'value', COALESCE(operand0 ->> 'operand_literal_unit', '')),
                jsonb_build_object('id', 'operator',           'type', 'string', 'name', 'Operator',
                                   'value', COALESCE(operators_arr ->> 0, '+')),
                jsonb_build_object('id', 'right_mode',          'type', 'string', 'name', 'Right Mode',
                                   'value', COALESCE(operand1 ->> 'mode', 'element')),
                jsonb_build_object('id', 'right_operand_id',    'type', 'string', 'name', 'Right Operand',
                                   'value', COALESCE(operand1 ->> 'operand_id', '')),
                jsonb_build_object('id', 'right_literal',       'type', 'string', 'name', 'Right Literal',
                                   'value', COALESCE(operand1 ->> 'operand_literal', '')),
                jsonb_build_object('id', 'right_literal_value', 'type', 'string', 'name', 'Right Literal Value',
                                   'value', COALESCE(operand1 ->> 'operand_literal_value', '')),
                jsonb_build_object('id', 'right_literal_unit',  'type', 'string', 'name', 'Right Literal Unit',
                                   'value', COALESCE(operand1 ->> 'operand_literal_unit', ''))
            );
            element := jsonb_set(element, '{fields}', new_fields);
            new_elements := new_elements || jsonb_build_array(element);
        END LOOP;

        IF rec.tbl = 'cds_artifact' THEN
            UPDATE cds_artifact SET base_elements = new_elements::text WHERE id = rec.id;
        ELSE
            UPDATE ecqm_artifact SET base_elements = new_elements::text WHERE id = rec.id;
        END IF;
    END LOOP;
END$$;
