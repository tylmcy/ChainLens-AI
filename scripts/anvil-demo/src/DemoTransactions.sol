// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.30;

/// @notice 只用于 ChainLens 本地 Anvil 演示，不是可用于生产的 ERC-20。
/// @dev 函数刻意复用精选 selector，使每条风险规则能被独立、确定地演示。
contract DemoTransactions {
    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);

    function approve(address spender, uint256 value) external returns (bool) {
        emit Approval(msg.sender, spender, value);
        return true;
    }

    function burn(uint256 value) external {
        emit Transfer(msg.sender, address(0), value);
    }

    /// @dev value 作为演示事件数；M4 传 21，越过 MANY-TOKEN-TRANSFERS 的中风险阈值。
    function transfer(address to, uint256 value) external returns (bool) {
        require(value <= 128, "demo limit");
        for (uint256 i = 0; i < value; i++) {
            emit Transfer(msg.sender, address(uint160(uint256(uint160(to)) + i)), 1);
        }
        return true;
    }

    /// @dev mint selector 在 ChainLens 精选表中，故失败场景只突出 TX-FAILED。
    function mint(address, uint256) external pure {
        revert("intentional demo failure");
    }

    /// @dev 接受未知 selector，供 UNKNOWN-SELECTOR 场景产生一笔成功交易。
    fallback() external payable {}
}
