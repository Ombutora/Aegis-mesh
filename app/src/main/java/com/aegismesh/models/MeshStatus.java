package com.aegismesh.models;

import java.io.Serializable;

public abstract class MeshStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    public static class Online extends MeshStatus {
        private static final long serialVersionUID = 1L;
    }

    public static class OfflineRelay extends MeshStatus {
        private static final long serialVersionUID = 1L;
        public final int nearbyPeerCount;

        public OfflineRelay(int nearbyPeerCount) {
            this.nearbyPeerCount = nearbyPeerCount;
        }
    }

    public static class Disconnected extends MeshStatus {
        private static final long serialVersionUID = 1L;
    }
}
