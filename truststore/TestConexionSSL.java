import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class TestConexionSSL {
    public static void main(String[] args) throws Exception {
        String host   = System.getenv().getOrDefault("IFX_HOST", "10.26.160.12");
        String port   = System.getenv().getOrDefault("IFX_PORT", "49152");
        String db     = System.getenv().getOrDefault("IFX_DB", "bdinteg");
        String server = System.getenv().getOrDefault("IFX_SERVER", "bancoppel2_ssl");

        // Truststore (JKS) para el JDBC
        String truststorePath = System.getenv().getOrDefault("SSL_TRUSTSTORE", "/etc/truststore/client_truststore.jks");
        String truststorePass = System.getenv().getOrDefault("SSL_TRUSTSTORE_PASSWORD", "CLIENTPASS");

        // Certificado público (CRT) para validar con openssl
        String caCertPath     = System.getenv().getOrDefault("SSL_CA_CERT", "/etc/truststore/bancoppel2_shm.crt");

        String user = System.getenv().getOrDefault("DB_USER", "sysinfmix");
        String pass = System.getenv().getOrDefault("DB_PASS", "TRxzs24%\"");

        String url = String.format(
             "jdbc:informix-sqli://%s:%s/%s:INFORMIXSERVER=%s;SSLCONNECTION=true;SSL_TRUSTSTORE=%s;SSL_TRUSTSTORE_PASSWORD=%s",
             host, port, db, server, truststorePath, truststorePass
        );

        // 0) Validación previa con OpenSSL (no bloqueante)
        validateWithOpenSSL(host, port, caCertPath);

        // 1) Conexión JDBC + consultas
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(1);
        cfg.setConnectionTimeout(10000);

        try (HikariDataSource ds = new HikariDataSource(cfg);
             Connection con = ds.getConnection();
             Statement stmt = con.createStatement()) {

            // Información de conexión
            System.out.println("✅ Conexión SSL exitosa con Informix!");
            System.out.println("✅ Detalles de conexión:");
            System.out.println("   - Host: " + host);
            System.out.println("   - Puerto: " + port);
            System.out.println("   - Base de datos: " + db);
            System.out.println("   - Servidor INFORMIXSERVER: " + server);
            System.out.println("   - Usuario: " + user);
            System.out.println("   - Truststore (JKS): " + truststorePath);
            System.out.println("   - CA (CRT) para OpenSSL: " + caCertPath);

            // 1️⃣ Versión de Informix
            String sqlVersion = "SELECT DBINFO('version','full') AS version FROM systables WHERE tabid = 1";
            try (ResultSet rsVer = stmt.executeQuery(sqlVersion)) {
                if (rsVer.next()) {
                    System.out.println("✅ Versión Informix: " + rsVer.getString("version"));
                }
            }

            // 2️⃣ Conteo en si_cliente
            String sqlCount = "SELECT COUNT(*) AS total FROM si_cliente";
            try (ResultSet rsCnt = stmt.executeQuery(sqlCount)) {
                if (rsCnt.next()) {
                    int total = rsCnt.getInt("total");
                    System.out.println("✅ Número de registros en si_cliente: " + total);
                }
            }
        }
    }

    // Ejecuta: openssl s_client -connect host:port -CAfile caCert -servername host -showcerts </dev/null
    private static void validateWithOpenSSL(String host, String port, String caCertPath) {
        System.out.println("🔎 Validación SSL con OpenSSL (no bloqueante)...");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "sh", "-lc",
                "openssl s_client -connect " + host + ":" + port +
                " -CAfile " + escape(caCertPath) +
                " -servername " + host +
                " -showcerts </dev/null | tail -n 5"
            );
            Process p = pb.start();

            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                 BufferedReader e = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {

                StringBuilder out = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
                StringBuilder err = new StringBuilder();
                while ((line = e.readLine()) != null) err.append(line).append('\n');

                int code = p.waitFor();
                String output = out.toString();
                if (code == 0 && output.contains("Verify return code: 0 (ok)")) {
                    System.out.println("✅ OpenSSL: verificación OK (cert confiable y handshake correcto)");
                } else {
                    System.out.println("⚠️ OpenSSL: la verificación no fue OK (revisa CAfile, CN/SAN y puerto SSL)");
                    if (!output.isEmpty()) System.out.print(output);
                    if (!err.toString().isEmpty()) System.out.print(err.toString());
                }
            }
        } catch (Exception ex) {
            System.out.println("ℹ️ OpenSSL no disponible o error al ejecutar (se continúa con JDBC): " + ex.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("'", "'\\''");
    }
}
