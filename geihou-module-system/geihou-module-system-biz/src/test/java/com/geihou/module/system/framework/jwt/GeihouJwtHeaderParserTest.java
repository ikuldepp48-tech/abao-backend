package com.geihou.module.system.framework.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeihouJwtHeaderParserTest {

    private static final Instant NOW = Instant.parse("2026-06-13T08:00:00Z");

    private final GeihouJwtHeaderParser parser = new GeihouJwtHeaderParser();

    @Test
    void shouldParseValidHs512JwtHeader() {
        Optional<GeihouJwtHeader> header = parser.parse(
                GeihouJwtTestTokenFactory.validToken(NOW, GeihouJwtTestTokenFactory.testSecret()));

        assertThat(header).isPresent();
        assertThat(header.get().kid()).isEqualTo(GeihouJwtTestTokenFactory.KID);
        assertThat(header.get().algorithm()).isEqualTo("HS512");
        assertThat(header.get().type()).isEqualTo("JWT");
    }

    @Test
    void shouldReturnEmptyForMalformedToken() {
        assertThat(parser.parse("not-a-token")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse(" ")).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenKidIsMissingOrBlank() {
        assertThat(parser.parse(tokenWithHeader(GeihouJwtTestTokenFactory.header(
                JWSAlgorithm.HS512, JOSEObjectType.JWT, null)))).isEmpty();
        assertThat(parser.parse(tokenWithHeader(GeihouJwtTestTokenFactory.header(
                JWSAlgorithm.HS512, JOSEObjectType.JWT, " ")))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenAlgorithmIsNotHs512() {
        assertThat(parser.parse(tokenWithHeader(GeihouJwtTestTokenFactory.header(
                JWSAlgorithm.HS256, JOSEObjectType.JWT, "kid-1")))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenTypeIsMissingOrWrong() {
        assertThat(parser.parse(tokenWithHeader(GeihouJwtTestTokenFactory.header(
                JWSAlgorithm.HS512, null, "kid-1")))).isEmpty();
        assertThat(parser.parse(tokenWithHeader(GeihouJwtTestTokenFactory.header(
                JWSAlgorithm.HS512, new JOSEObjectType("JWS"), "kid-1")))).isEmpty();
    }

    @Test
    void shouldParseHeaderWithoutValidClaimsOrSignature() {
        String token = Base64URL.encode("{\"alg\":\"HS512\",\"typ\":\"JWT\",\"kid\":\"kid-1\"}")
                + "." + Base64URL.encode("not-json-claims")
                + "." + Base64URL.encode("not-a-valid-signature");

        Optional<GeihouJwtHeader> header = parser.parse(token);

        assertThat(header).isPresent();
        assertThat(header.get().kid()).isEqualTo("kid-1");
    }

    private static String tokenWithHeader(JWSHeader header) {
        return GeihouJwtTestTokenFactory.sign(
                header,
                GeihouJwtTestTokenFactory.validClaimsBuilder(NOW).build(),
                GeihouJwtTestTokenFactory.testSecret());
    }
}
