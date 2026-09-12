package com.example.worldbrowser.mixin;

import com.example.worldbrowser.registry.WorldBrowserRegistry;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

@Mixin(LevelStorageSource.class)
public class LevelStorageSourceMixin {

    @Inject(method = "getLevelPath", at = @At("HEAD"), cancellable = true)
    private void worldbrowser_onGetLevelPath(String levelId, CallbackInfoReturnable<Path> cir) {
        Path customPath = WorldBrowserRegistry.getInstance().getWorldPath(levelId);
        if (customPath != null) {
            cir.setReturnValue(customPath);
        }
    }
}

