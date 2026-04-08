package android.net;

import java.util.concurrent.Executor;

public class TetheringManager {

    public static class TetheringRequest {

        public static class Builder {

            public Builder(int type) {
            }

            public Builder setStaticIpv4Addresses(LinkAddress localIPv4Address, LinkAddress clientAddress) {
                return null;
            }

            public TetheringRequest build() {
                return null;
            }
        }
    }

    public interface StartTetheringCallback {

        default void onTetheringStarted() {}

        default void onTetheringFailed(int error) {}
    }

    public void startTethering(TetheringRequest request, Executor executor, StartTetheringCallback callback) {
    }

    public void stopTethering(int type) {
    }
}
