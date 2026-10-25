package demoday.backend.payment.client.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayAppPaymentRequestTest {

    @Test
    void acceptsValidPaymentRequest() {
        PayAppPaymentRequest request = new PayAppPaymentRequest(
                "PAY20261025120000ABCDEF",
                "일주일 패스",
                1900,
                "01012345678"
        );

        assertThat(request.amount()).isEqualTo(1900);
        assertThat(request.receiverPhone())
                .isEqualTo("01012345678");
    }

    @Test
    void rejectsInvalidRequiredValues() {
        assertThatThrownBy(() -> new PayAppPaymentRequest(
                "",
                "일주일 패스",
                1900,
                "01012345678"
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PayAppPaymentRequest(
                "PAY1",
                "",
                1900,
                "01012345678"
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PayAppPaymentRequest(
                "PAY1",
                "일주일 패스",
                0,
                "01012345678"
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PayAppPaymentRequest(
                "PAY1",
                "일주일 패스",
                1900,
                "010-1234-5678"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
