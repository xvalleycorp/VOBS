package io.vobs.switching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vobs.model.BankId;
import io.vobs.model.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class SimulatorSwitchTest {

    private static final BankId SENDER = BankId.ofBin("970436");
    private static final BankId RECEIVER = BankId.ofBin("970415");

    private final SimulatorSwitch simulator = new SimulatorSwitch("sim", Money.vnd(500_000_000),
            Clock.fixed(Instant.parse("2026-10-24T03:00:00Z"), ZoneOffset.UTC))
            .addAccount(RECEIVER, "113366668888", "TRAN THI BINH");

    private static TransferRequest transfer(String reference, long amount, String creditorAccount) {
        return new TransferRequest(reference, Money.vnd(amount), SENDER, "0011004567890", "NGUYEN VAN AN",
                RECEIVER, creditorAccount, "Thanh toan");
    }

    @Test
    void settlesValidTransfer() {
        TransferResult result = simulator.submit(transfer("REF-1", 1_000_000, "113366668888"));

        assertEquals(TransferResult.Status.SETTLED, result.status());
        assertEquals("SIM-0000000001", result.switchReference());
        assertTrue(result.isFinal());
        assertEquals(result, simulator.status("REF-1").orElseThrow());
    }

    @Test
    void looksUpAccountName() {
        assertEquals("TRAN THI BINH", simulator.inquireAccountName(RECEIVER, "113366668888").orElseThrow());
        assertTrue(simulator.inquireAccountName(RECEIVER, "999").isEmpty());
    }

    @Test
    void rejectsUnknownCreditorAndOverLimit() {
        assertEquals("AC03", simulator.submit(transfer("REF-2", 1_000, "999")).reasonCode());
        assertEquals("AM02", simulator.submit(transfer("REF-3", 600_000_000, "113366668888")).reasonCode());
    }

    @Test
    void resubmittingSameRequestIsIdempotent() {
        TransferResult first = simulator.submit(transfer("REF-4", 1_000, "113366668888"));
        TransferResult again = simulator.submit(transfer("REF-4", 1_000, "113366668888"));

        assertSame(first, again);
    }

    @Test
    void reusingReferenceForDifferentRequestIsRejectedWithoutTouchingOriginal() {
        TransferResult original = simulator.submit(transfer("REF-5", 1_000, "113366668888"));
        TransferResult conflict = simulator.submit(transfer("REF-5", 2_000, "113366668888"));

        assertEquals("AM05", conflict.reasonCode());
        assertEquals(original, simulator.status("REF-5").orElseThrow());
    }

    @Test
    void registryResolvesAdaptersById() {
        SwitchRegistry registry = new SwitchRegistry().register(simulator);

        assertSame(simulator, registry.get("sim"));
        assertThrows(IllegalStateException.class, () -> registry.register(simulator));
        assertThrows(NoSuchElementException.class, () -> registry.get("napas"));
    }
}
