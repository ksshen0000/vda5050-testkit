package io.github.ksshen0000.vda5050.client;

import io.github.ksshen0000.vda5050.v2.Vda5050Message;
import io.github.ksshen0000.vda5050.v2.Vda5050Topic;

/**
 * Sees every message a {@link MasterClient} sends or receives, e.g. for a message log. Called on the
 * transport's threads; implementations must be quick and must not throw.
 */
public interface MessageTap {

    /** @param payload the encoded message exactly as published, header included */
    void sent(VehicleId vehicle, Vda5050Topic topic, Vda5050Message message, byte[] payload);

    /** @param payload the message exactly as received */
    void received(VehicleId vehicle, Vda5050Topic topic, Vda5050Message message, byte[] payload);

    MessageTap NONE = new MessageTap() {
        @Override
        public void sent(VehicleId vehicle, Vda5050Topic topic, Vda5050Message message, byte[] payload) {
        }

        @Override
        public void received(VehicleId vehicle, Vda5050Topic topic, Vda5050Message message, byte[] payload) {
        }
    };
}
