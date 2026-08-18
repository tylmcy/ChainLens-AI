package io.chainlens.model;

/**
 * 网络标识：由 eth_chainId 映射。报告必须标注网络，未知网络如实标注 UNKNOWN。
 */
public enum Network {

    MAINNET(1L, "mainnet"),
    SEPOLIA(11155111L, "sepolia"),
    HOLESKY(17000L, "holesky"),
    ANVIL(31337L, "anvil-local"),
    UNKNOWN(-1L, "unknown");

    private final long chainId;
    private final String label;

    Network(long chainId, String label) {
        this.chainId = chainId;
        this.label = label;
    }

    public long chainId() {
        return chainId;
    }

    public String label() {
        return label;
    }

    public static Network fromChainId(long chainId) {
        for (Network network : values()) {
            if (network.chainId == chainId) {
                return network;
            }
        }
        return UNKNOWN;
    }
}
