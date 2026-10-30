package com.ragemod.mixin;

import com.ragemod.core.NickColors;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Recolors the nick tag above players. */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
	private void ragemod$nick(CallbackInfoReturnable<Component> cir) {
		Object self = this;
		if (self instanceof AbstractClientPlayer p) {
			cir.setReturnValue(NickColors.nick(p.getName().getString(), cir.getReturnValue()));
		}
	}
}
