package jp.jyn.ebifly.listener;

import jp.jyn.ebifly.PluginMain;
import jp.jyn.ebifly.config.MainConfig;
import jp.jyn.ebifly.fly.FlyRepository;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.function.Consumer;

public class RestrictListener implements Listener {
    private final FlyRepository fly;

    private final boolean levitationStop;
    private final boolean waterStop;
    private final Consumer<Runnable> syncCall;
    private final Consumer<Player> levitationHandler;
    private final MainConfig config;

    public RestrictListener(PluginMain plugin, MainConfig config, FlyRepository fly,
                            Consumer<Runnable> syncCall, Consumer<Player> levitationHandler) {
        this.fly = fly;
        this.syncCall = syncCall;
        this.levitationHandler = levitationHandler;
        this.config = config;

        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
        // 必要ない物をアンロード
        if (!config.restrictRespawn) {
            PlayerRespawnEvent.getHandlerList().unregister(this);
        }
        if (!config.restrictWorld) {
            PlayerChangedWorldEvent.getHandlerList().unregister(this);
        }
        if (!config.restrictGamemode) {
            PlayerGameModeChangeEvent.getHandlerList().unregister(this);
        }
        if (config.restrictLevitation == null) {
            EntityPotionEffectEvent.getHandlerList().unregister(this);
        }
        if (config.restrictWater == null) {
            EntityAirChangeEvent.getHandlerList().unregister(this);
        }
        if (config.restrictFlySpeed == 0.1) {
            PlayerToggleFlightEvent.getHandlerList().unregister(this);
        }

        // nullの時は使わない
        levitationStop = config.restrictLevitation != null ? config.restrictLevitation : false;
        waterStop = config.restrictWater != null ? config.restrictWater : false;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public final void onPlayerRespawn(PlayerRespawnEvent e) {
        var p = e.getPlayer();
        if (fly.isFlying(p)) {
            // リスポーン(死亡、エンドからの飛行)時はflyが解ける
            if (p.hasPermission("ebifly.restrict.respawn")) {
                p.setAllowFlight(true); // なのでもう一度飛べるように
            } else {
                fly.stopRefund(p); // なので停止する
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public final void onPlayerChangedWorld(PlayerChangedWorldEvent e) {
        // 世界変更時はflyはそのまま
        var p = e.getPlayer();
        if (p.hasPermission("ebifly.restrict.world")) {
            if (fly.isFlying(p)) { // テレポートで移動すると止まるので入れ直す
                p.setAllowFlight(true);
            }
        } else {
            fly.stopRefund(p); // なので権限がない時だけ停止する
        }
    }

    private boolean isFlightMode(GameMode mode) {
        return switch (mode) {
            case CREATIVE, SPECTATOR -> true;
            case SURVIVAL, ADVENTURE -> false;
        };
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public final void onPlayerGameModeChange(PlayerGameModeChangeEvent e) {
        var p = e.getPlayer();
        if (p.hasPermission("ebifly.restrict.gamemode")) {
            // 飛べないモードに切り替えると落とされる(サバイバル<->アドベンチャー間でも)
            if (!isFlightMode(e.getNewGameMode()) && fly.isFlying(p)) {
                syncCall.accept(() -> p.setAllowFlight(true)); // ので、再度有効化してあげる
                // 1tickズラさないとダメ
            }
        } else {
            // 飛んだままクリエイティブやスペクテイターに切り替えると飛ばないのに飛行時間を食う
            if (isFlightMode(e.getNewGameMode())) {
                fly.stopRefund(p); // ので、止めてあげる
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public final void onEntityPotionEffect(EntityPotionEffectEvent e) {
        if (e.getEntityType() != EntityType.PLAYER || !e.getModifiedType().equals(PotionEffectType.LEVITATION)) {
            return;
        }

        var p = (Player) e.getEntity();
        if (p.hasPermission("ebifly.restrict.levitation")) {
            return;
        }

        // true -> ADDで停止
        // false -> ADDで飛行停止、REMOVED/CLEAREDで再度追加
        // たぶんJVMが最適化する
        if (levitationStop) {
            if (e.getAction() == EntityPotionEffectEvent.Action.ADDED) {
                fly.stopRefund(p);
                levitationHandler.accept(p);
            }
        } else {
            switch (e.getAction()) {
                case ADDED -> {
                    if (!isFlightMode(p.getGameMode()) && fly.isFlying(p)) {
                        p.setAllowFlight(false); // 一時的に飛ばせない
                    }
                }
                case REMOVED, CLEARED -> {
                    if (fly.isFlying(p)) {
                        p.setAllowFlight(true); // もう一度飛べるように
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public final void onEntityAirChange(EntityAirChangeEvent e) {
        if (e.getEntityType() != EntityType.PLAYER) {
            return;
        }

        var p = (Player) e.getEntity();
        if (p.hasPermission("ebifly.restrict.water")) {
            return;
        }

        // JVMが最適化する
        if (waterStop) {
            // このイベントが出た時点で水中に入ったのは確定
            fly.stopRefund(p);
        } else if (fly.isFlying(p)) {
            // Entity#isInWaterは足が浸かってたらtrueになる == 水面で息継ぎするとイベント出なくなって戻せなくなる
            // 減ってる == 水中 / 増えてる == 水上
            p.setAllowFlight(e.getAmount() >= p.getRemainingAir());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public final void onFly(PlayerToggleFlightEvent e) {
        if (fly.isFlying(e.getPlayer())) {
            e.getPlayer().setFlySpeed(config.restrictFlySpeed);
        }
    }
}
