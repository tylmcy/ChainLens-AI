package io.chainlens.rules.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuiltInRiskAddressListTest {

    @Test
    @DisplayName("从 classpath 加载内置演示列表（2 条，来源明确标注）")
    void loadsDefaultResource() {
        BuiltInRiskAddressList list = BuiltInRiskAddressList.fromClasspath(BuiltInRiskAddressList.DEFAULT_RESOURCE);

        assertThat(list.entries()).hasSize(2);
        var hit = list.find("0xdead000000000000000000000000000000000001");
        assertThat(hit).isPresent();
        assertThat(hit.get().source()).isEqualTo("chainlens-builtin-sample");
        assertThat(hit.get().label()).contains("虚构样例");
    }

    @Test
    @DisplayName("大小写不敏感查找")
    void caseInsensitiveLookup() {
        BuiltInRiskAddressList list = new BuiltInRiskAddressList("test");
        list.add("0x" + "ab".repeat(20), "标签", "src");

        assertThat(list.find("0x" + "AB".repeat(20))).isPresent();
    }

    @Test
    @DisplayName("不存在的资源 fail-fast")
    void missingResourceFailsFast() {
        assertThatThrownBy(() -> BuiltInRiskAddressList.fromClasspath("does-not-exist.json"))
                .isInstanceOf(IllegalStateException.class);
    }
}
