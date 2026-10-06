package com.ruralmarket.platform.service;

import com.ruralmarket.platform.service.LotteryService.AppInput;
import com.ruralmarket.platform.service.LotteryService.StallInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 抽签算法核心规则与确定性测试 */
class LotteryServiceTest {
    private final LotteryService service = new LotteryService(null, null, null, null, null, null, null, null);

    private static StallInput stall(long id, String no, boolean power, Double maxLen,
                                    boolean cooked, boolean poultry, String cats) {
        return new StallInput(id, no, no.substring(0, 1), power, maxLen, cooked, poultry, cats);
    }

    @Test
    void deterministic_sameSeedSameResult() {
        List<AppInput> apps = List.of(
                new AppInput(1, "COOKED", true, true, false, 3.0, true, 5),
                new AppInput(2, "VEG_FRUIT", false, false, false, 4.0, false, 1),
                new AppInput(3, "LIVE_POULTRY", false, false, true, 5.0, false, 2));
        List<StallInput> stalls = List.of(
                stall(11, "B-01", true, 4.0, true, false, "COOKED"),
                stall(12, "D-01", false, 5.0, false, false, "VEG_FRUIT"),
                stall(13, "C-01", false, 6.0, false, true, "LIVE_POULTRY"));
        Map<Long, Long> r1 = service.assign(apps, stalls, 42L);
        Map<Long, Long> r2 = service.assign(apps, stalls, 42L);
        assertEquals(r1, r2, "相同种子与输入必须得到相同分配");
        assertEquals(3, r1.size());
    }

    @Test
    void cookedFoodOnlyToColdChainZone() {
        List<AppInput> apps = List.of(new AppInput(1, "COOKED", true, true, false, 3.0, false, 0));
        List<StallInput> stalls = List.of(
                stall(11, "A-01", true, 5.0, false, false, "*"),   // 有电但不许熟食
                stall(12, "B-01", true, 4.0, true, false, "COOKED")); // 冷链区
        Map<Long, Long> r = service.assign(apps, stalls, 7L);
        assertEquals(12L, r.get(1L), "熟食必须分配到冷链区");
    }

    @Test
    void livePoultryOnlyToIsolatedZone() {
        List<AppInput> apps = List.of(new AppInput(1, "LIVE_POULTRY", false, false, true, 5.0, false, 0));
        List<StallInput> stalls = List.of(
                stall(11, "A-01", false, 6.0, false, false, "*"),
                stall(12, "C-01", false, 6.0, false, true, "LIVE_POULTRY"));
        Map<Long, Long> r = service.assign(apps, stalls, 7L);
        assertEquals(12L, r.get(1L), "活禽必须分配到隔离区");
    }

    @Test
    void powerAndVehicleConstraints() {
        // 需要电 + 车长5.5米：只有 A-02 同时满足
        List<AppInput> apps = List.of(new AppInput(1, "DAILY_GOODS", true, false, false, 5.5, false, 0));
        List<StallInput> stalls = List.of(
                stall(11, "A-01", false, 8.0, false, false, "*"),  // 无电
                stall(12, "A-02", true, 6.0, false, false, "*"),   // 满足
                stall(13, "A-03", true, 4.0, false, false, "*")); // 车长超限
        Map<Long, Long> r = service.assign(apps, stalls, 7L);
        assertEquals(12L, r.get(1L));
    }

    @Test
    void noCompatibleStallMeansLose() {
        List<AppInput> apps = List.of(new AppInput(1, "LIVE_POULTRY", false, false, true, 5.0, false, 0));
        List<StallInput> stalls = List.of(stall(11, "A-01", false, 6.0, false, false, "*"));
        Map<Long, Long> r = service.assign(apps, stalls, 7L);
        assertFalse(r.containsKey(1L), "无兼容摊位应未中签");
    }

    @Test
    void categoryZoneRestriction() {
        List<AppInput> apps = List.of(new AppInput(1, "CLOTHING", false, false, false, null, false, 0));
        List<StallInput> stalls = List.of(
                stall(11, "D-01", false, 5.0, false, false, "VEG_FRUIT"), // 只允许果蔬
                stall(12, "A-01", false, 5.0, false, false, "DAILY_GOODS,CLOTHING"));
        Map<Long, Long> r = service.assign(apps, stalls, 7L);
        assertEquals(12L, r.get(1L), "品类必须匹配区域允许范围");
    }
}
