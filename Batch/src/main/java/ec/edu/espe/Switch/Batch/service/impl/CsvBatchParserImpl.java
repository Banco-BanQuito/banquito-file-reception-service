package ec.edu.espe.Switch.Batch.service.impl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import ec.edu.espe.Switch.Batch.config.FileReceptionProperties;
import ec.edu.espe.Switch.Batch.dto.ParsedBatch;
import ec.edu.espe.Switch.Batch.dto.ParsedPaymentLine;
import ec.edu.espe.Switch.Batch.service.ICsvBatchParser;

@Service
public class CsvBatchParserImpl implements ICsvBatchParser {

    private final FileReceptionProperties properties;

    public CsvBatchParserImpl(FileReceptionProperties properties) {
        this.properties = properties;
    }

    @Override
    public ParsedBatch parse(InputStream inputStream, String serviceType, String clientRuc) throws IOException {
        byte[] raw = inputStream.readAllBytes();
        String hash = sha256Hex(raw);
        List<String> lines = readNonEmptyLines(raw);

        if (lines.size() < 3) {
            throw new IllegalArgumentException("El archivo debe contener cabecera, al menos un detalle y pie");
        }

        String[] header = split(lines.get(0));
        String[] footer = split(lines.get(lines.size() - 1));

        if (header.length != 6) {
            throw new IllegalArgumentException("Cabecera invalida: se esperan 6 campos [ruc,servicio,fecha,cuenta_matriz,total_registros,monto_total]");
        }
        if (footer.length != 3) {
            throw new IllegalArgumentException("Pie invalido: se esperan 3 campos [codigo_seguridad, registros, monto]");
        }

        String headerRuc = header[0].trim();
        String headerServiceType = header[1].trim();
        validateHeaderMetadata(header, footer);

        String resolvedRuc = hasText(clientRuc) ? clientRuc.trim() : headerRuc;
        String resolvedServiceType = hasText(serviceType) ? serviceType.trim() : headerServiceType;
        if (!hasText(resolvedRuc)) {
            throw new IllegalArgumentException("clientRuc es obligatorio");
        }
        if (!hasText(resolvedServiceType)) {
            throw new IllegalArgumentException("serviceType es obligatorio");
        }
        if (hasText(clientRuc) && hasText(headerRuc) && !clientRuc.trim().equals(headerRuc)) {
            throw new IllegalArgumentException("clientRuc no coincide con el RUC declarado en cabecera");
        }
        if (hasText(serviceType) && hasText(headerServiceType) && !serviceType.trim().equalsIgnoreCase(headerServiceType)) {
            throw new IllegalArgumentException("serviceType no coincide con el tipo declarado en cabecera");
        }

        int headerRecords = parsePositiveInt(header[4], "total de registros de cabecera");
        BigDecimal headerAmount = parseAmount(header[5], "monto total de cabecera");
        int footerRecords = parsePositiveInt(footer[1], "total de registros del pie");
        BigDecimal footerAmount = parseAmount(footer[2], "monto total del pie");

        if (headerRecords != footerRecords) {
            throw new IllegalArgumentException("Los registros declarados en cabecera y pie no coinciden");
        }
        if (headerAmount.compareTo(footerAmount) != 0) {
            throw new IllegalArgumentException("Los montos declarados en cabecera y pie no coinciden");
        }

        List<ParsedPaymentLine> details = new ArrayList<>();
        for (int i = 1; i < lines.size() - 1; i++) {
            details.add(parseDetail(lines.get(i), i, i + 1));
        }

        BigDecimal calculatedAmount = details.stream()
                .map(ParsedPaymentLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (details.size() != footerRecords) {
            throw new IllegalArgumentException("El numero de detalles no coincide con el total declarado");
        }
        if (calculatedAmount.compareTo(footerAmount) != 0) {
            throw new IllegalArgumentException("La suma de detalles no coincide con el monto declarado");
        }

        return new ParsedBatch(
                resolvedRuc,
                resolvedServiceType,
                header[3].trim(),
                hash,
                headerRecords,
                headerAmount,
                footerRecords,
                footerAmount,
                footer[0].trim(),
                details);
    }

    private ParsedPaymentLine parseDetail(String line, int expectedSequential, int physicalLineNumber) {
        String[] parts = split(line);
        if (parts.length != 8) {
            throw new IllegalArgumentException("Detalle invalido en linea " + physicalLineNumber + ": se esperan 8 campos [secuencial,routing_code,identificacion,nombre,cuenta_destino,monto,referencia,email]");
        }
        int lineNumber = parsePositiveInt(parts[0], "numero de linea del detalle");
        if (lineNumber != expectedSequential) {
            throw new IllegalArgumentException("Secuencial invalido en linea " + physicalLineNumber + ": esperado " + expectedSequential + " y recibido " + lineNumber);
        }
        return new ParsedPaymentLine(
                lineNumber,
                requireText(parts[1], "ROUTING_CODE", physicalLineNumber),
                requireText(parts[2], "identificacion del beneficiario", physicalLineNumber),
                requireText(parts[3], "nombre del beneficiario", physicalLineNumber),
                requireText(parts[4], "cuenta destino", physicalLineNumber),
                parseAmount(parts[5], "monto del detalle"),
                requireText(parts[6], "referencia", physicalLineNumber),
                requireText(parts[7], "correo de notificacion", physicalLineNumber));
    }

    private String requireText(String raw, String field, int physicalLineNumber) {
        if (!hasText(raw)) {
            throw new IllegalArgumentException(field + " es obligatorio en linea " + physicalLineNumber);
        }
        return raw.trim();
    }

    private void validateHeaderMetadata(String[] header, String[] footer) {
        try {
            LocalDateTime.parse(header[2].trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Fecha de generacion invalida en cabecera: " + header[2].trim(), e);
        }
        if (!hasText(header[3])) {
            throw new IllegalArgumentException("La cuenta origen de cabecera es obligatoria");
        }
        if (!hasText(footer[0])) {
            throw new IllegalArgumentException("El codigo de seguridad del pie es obligatorio");
        }
    }

    private List<String> readNonEmptyLines(byte[] raw) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(raw), StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        }
    }

    private String[] split(String line) {
        String delimiter = properties.getFileDelimiter();
        if (delimiter == null || delimiter.isEmpty()) {
            delimiter = ",";
        }
        return line.split(Pattern.quote(delimiter), -1);
    }

    private int parsePositiveInt(String raw, String field) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1) {
                throw new IllegalArgumentException(field + " debe ser mayor o igual a 1");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " no es numerico: " + raw.trim(), e);
        }
    }

    private BigDecimal parseAmount(String raw, String field) {
        try {
            BigDecimal amount = new BigDecimal(raw.trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(field + " debe ser mayor a cero");
            }
            if (amount.scale() != 2) {
                throw new IllegalArgumentException(field + " debe tener exactamente dos decimales");
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " no es legible: " + raw.trim(), e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no esta disponible", e);
        }
    }
}
