package com.cib.payment.api.infrastructure.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.AccountRelationshipRole;
import org.junit.jupiter.api.Test;

class FiCorrespondentRouteProfileTest {
    private final FiCorrespondentRouteProfile routeProfile = new FiCorrespondentRouteProfile();

    @Test
    void derivesNostroRouteForCibToCorrusUsd() {
        var context = routeProfile.derive("CIBBHKHH", "CORRUS33", "USD");

        assertThat(context.instructingAgent().bic()).isEqualTo("CIBBHKHH");
        assertThat(context.instructedAgent().bic()).isEqualTo("CORRUS33");
        assertThat(context.correspondentOrIntermediaryBank()).hasValueSatisfying(party ->
                assertThat(party.bic()).isEqualTo("CORRUS33"));
        assertThat(context.settlementCurrency()).isEqualTo("USD");
        assertThat(context.accountRelationshipRole()).isEqualTo(AccountRelationshipRole.NOSTRO);
        assertThat(context.maskedSimulatedAccountReference()).isEqualTo("nostro-usd-corrus33-****1234");
    }

    @Test
    void derivesVostroRouteForVostusToCibUsd() {
        var context = routeProfile.derive("VOSTUS33", "CIBBHKHH", "USD");

        assertThat(context.instructingAgent().bic()).isEqualTo("VOSTUS33");
        assertThat(context.instructedAgent().bic()).isEqualTo("CIBBHKHH");
        assertThat(context.correspondentOrIntermediaryBank()).hasValueSatisfying(party ->
                assertThat(party.bic()).isEqualTo("VOSTUS33"));
        assertThat(context.settlementCurrency()).isEqualTo("USD");
        assertThat(context.accountRelationshipRole()).isEqualTo(AccountRelationshipRole.VOSTRO);
        assertThat(context.maskedSimulatedAccountReference()).isEqualTo("vostro-usd-vostus33-****5678");
    }

    @Test
    void derivesLoroRouteForCibToLorousUsd() {
        var context = routeProfile.derive("CIBBHKHH", "LOROUS33", "USD");

        assertThat(context.instructingAgent().bic()).isEqualTo("CIBBHKHH");
        assertThat(context.instructedAgent().bic()).isEqualTo("LOROUS33");
        assertThat(context.correspondentOrIntermediaryBank()).hasValueSatisfying(party ->
                assertThat(party.bic()).isEqualTo("LOROUS33"));
        assertThat(context.settlementCurrency()).isEqualTo("USD");
        assertThat(context.accountRelationshipRole()).isEqualTo(AccountRelationshipRole.LORO);
        assertThat(context.maskedSimulatedAccountReference()).isEqualTo("loro-usd-lorous33-****9012");
    }

    @Test
    void rejectsUnknownRoute() {
        assertThatThrownBy(() -> routeProfile.derive("CIBBHKHH", "UNKNOWN33", "USD"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported FI correspondent route");
    }

    @Test
    void rejectsNonUsdRoute() {
        assertThatThrownBy(() -> routeProfile.derive("CIBBHKHH", "CORRUS33", "EUR"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Only USD FI correspondent routes are supported");
    }
}
