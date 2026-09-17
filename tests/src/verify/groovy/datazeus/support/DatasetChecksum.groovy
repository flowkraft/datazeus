package datazeus.support

import groovy.sql.Sql

import java.security.MessageDigest
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The academy's canonical table checksum — the SAME form the Seed Data scripts write into
 * `_dataset_info` (academy-northwind-co-install.groovy) and re-check (academy-verify.groovy):
 * every column, names sorted case-insensitively; NULL as <NULL>; numbers with trailing zeros
 * stripped; timestamps yyyy-MM-dd HH:mm:ss; dates yyyy-MM-dd; rows tab-joined and sorted; SHA-256.
 *
 * A gate that asserts figures on a dataset first proves the dataset is the one those figures
 * were measured on. If this differs, the fix is a reinstall, never a new expected value.
 */
class DatasetChecksum {

    /** Every table named in `schema`._dataset_info whose row count or checksum differs, as readable lines. */
    static List<String> problems(Sql sql, String schema) {
        List<String> out = []
        sql.rows("SELECT \"TableName\", \"RowCount\", \"Checksum\" FROM ${schema}.\"_dataset_info\"".toString()).each { row ->
            String table = row.TableName
            List<String> cols = sql.rows('SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?',
                    [schema, table]).collect { it.column_name as String }.sort { a, b -> a.compareToIgnoreCase(b) }
            List<String> lines = []
            sql.eachRow("SELECT ${cols.collect { q(it) }.join(', ')} FROM ${schema}.${q(table)}".toString()) { r ->
                lines << (1..cols.size()).collect { canon(r.getObject(it)) }.join('\t')
            }
            lines.sort()
            String hex = MessageDigest.getInstance('SHA-256').digest(lines.join('\n').getBytes('UTF-8'))
                    .collect { String.format('%02x', it) }.join()
            if (lines.size() != (row.RowCount as int)) out << "${table}: ${lines.size()} rows, expected ${row.RowCount}".toString()
            else if (hex != row.Checksum) out << "${table}: same row count, different content".toString()
        }
        out
    }

    private static String canon(Object v) {
        if (v == null) return '<NULL>'
        if (v instanceof Boolean) return v ? 'true' : 'false'
        if (v instanceof Number) { String s = new BigDecimal(v.toString()).stripTrailingZeros().toPlainString(); return s == '-0' ? '0' : s }
        if (v instanceof Timestamp) return ((Timestamp) v).toLocalDateTime().toString().replace('T', ' ').padRight(19, ':00').substring(0, 19)
        if (v instanceof LocalDateTime) return v.toString().replace('T', ' ').padRight(19, ':00').substring(0, 19)
        if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate().toString()
        if (v instanceof LocalDate) return v.toString()
        return v.toString()
    }

    private static String q(String id) { '"' + id.replace('"', '""') + '"' }
}
