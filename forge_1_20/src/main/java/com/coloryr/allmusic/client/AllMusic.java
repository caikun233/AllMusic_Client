package com.coloryr.allmusic.client;

import com.coloryr.allmusic.client.hud.ComType;
import com.coloryr.allmusic.client.hud.HudUtils;
import com.coloryr.allmusic.client.player.APlayer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraftforge.client.event.sound.SoundEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.nio.charset.StandardCharsets;

@Mod("allmusic_client")
public class AllMusic {
    private static APlayer nowPlaying;
    private static HudUtils hudUtils;
    private static GuiGraphics gui;
    private static final ResourceLocation channel = new ResourceLocation("allmusic", "channel");

    public AllMusic() {

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::setup1);
        modEventBus.addListener(this::onLoad);

        MinecraftForge.EVENT_BUS.register(this);
    }

    public static void sendMessage(String data) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().gui.getChat().addMessage(Component.literal(data));
        });
    }

    private void setup(final FMLClientSetupEvent event) {
        hudUtils = new HudUtils(FMLPaths.CONFIGDIR.get());
        NetworkRegistry.ChannelBuilder.named(channel)
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(((status) -> true))
                .serverAcceptedVersions(((status) -> true))
                .eventNetworkChannel()
                .addListener(this::handel);
    }

    private void setup1(final FMLLoadCompleteEvent event) {
        nowPlaying = new APlayer();
    }

    private void handel(NetworkEvent.ServerCustomPayloadEvent event) {
        try {
            var buf = event.getPayload();
            byte type = buf.readByte();
            if (type >= HudUtils.types.length || type < 0) {
                return;
            }
            ComType type1 = ComType.values()[type];
            switch (type1) {
                case lyric:
                    hudUtils.lyric = readString(buf);
                    break;
                case info:
                    hudUtils.info = readString(buf);
                    break;
                case list:
                    hudUtils.list = readString(buf);
                    break;
                case play:
                    Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC);
                    Minecraft.getInstance().getSoundManager().stop(null, SoundSource.RECORDS);
                    stopPlaying();
                    nowPlaying.setMusic(readString(buf));
                    break;
                case img:
                    hudUtils.setImg(readString(buf));
                    break;
                case stop:
                    stopPlaying();
                    break;
                case clear:
                    hudUtils.close();
                    break;
                case pos:
                    nowPlaying.set(buf.readInt());
                    break;
                case hud:
                    hudUtils.setPos(readString(buf));
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String readString(ByteBuf buf) {
        int size = buf.readInt();
        byte[] temp = new byte[size];
        buf.readBytes(temp);

        return new String(temp, StandardCharsets.UTF_8);
    }

    public static int getScreenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public static int getScreenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    public static int getTextWidth(String item) {
        return Minecraft.getInstance().font.width(item);
    }

    public static int getFontHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }

    public void onLoad(final SoundEngineLoadEvent e) {
        if (nowPlaying != null) {
            nowPlaying.setReload();
        }
    }

    @SubscribeEvent
    public void onSound(final SoundEvent.SoundSourceEvent e) {
        if (!nowPlaying.isPlay())
            return;
        SoundSource data = e.getSound().getSource();
        switch (data) {
            case MUSIC, RECORDS -> e.getChannel().stop();
        }
    }

    @SubscribeEvent
    public void onServerQuit(final ClientPlayerNetworkEvent.LoggingOut e) {
        try {
            stopPlaying();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        hudUtils.save = null;
    }

    public static float getVolume() {
        return Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS);
    }

    public static void drawPic(int textureID, int size, int x, int y, int ang) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, textureID);

        PoseStack stack = new PoseStack();
        Matrix4f matrix = stack.last().pose();

        int a = size / 2;

        if (ang > 0) {
            matrix = matrix.translationRotate(x + a, y + a, 0,
                    new Quaternionf().fromAxisAngleDeg(0, 0, 1, ang));
        } else {
            matrix = matrix.translation(x + a, y + a, 0);
        }
        int x0 = -a;
        int x1 = a;
        int y0 = -a;
        int y1 = a;
        int z = 0;
        int u0 = 0;
        float u1 = 1;
        float v0 = 0;
        float v1 = 1;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix, (float) x0, (float) y1, (float) z).uv(u0, v1).endVertex();
        bufferBuilder.vertex(matrix, (float) x1, (float) y1, (float) z).uv(u1, v1).endVertex();
        bufferBuilder.vertex(matrix, (float) x1, (float) y0, (float) z).uv(u1, v0).endVertex();
        bufferBuilder.vertex(matrix, (float) x0, (float) y0, (float) z).uv(u0, v0).endVertex();

        BufferUploader.drawWithShader(bufferBuilder.end());

        //GuiComponent.blit(stack, x, y, 0, 0, 0, size, size, size, size);
    }

    public static void drawText(String item, int x, int y, int color, boolean shadow) {
        var hud = Minecraft.getInstance().font;
        gui.drawString(hud, item, x, y, color, shadow);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiOverlayEvent.Post e) {
        if (e.getOverlay().id() == VanillaGuiOverlay.PORTAL.id()) {
            gui = e.getGuiGraphics();
            hudUtils.update();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        nowPlaying.tick();
    }

    private void stopPlaying() {
        nowPlaying.closePlayer();
        hudUtils.close();
    }

    public static void runMain(Runnable runnable) {
        Minecraft.getInstance().execute(runnable);
    }
}
