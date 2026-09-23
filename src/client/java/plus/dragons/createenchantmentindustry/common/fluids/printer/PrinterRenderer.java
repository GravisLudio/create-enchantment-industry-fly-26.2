/*
 * Copyright (C) 2025  DragonsPlus
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package plus.dragons.createenchantmentindustry.common.fluids.printer;

import static plus.dragons.createenchantmentindustry.common.fluids.printer.PrinterBlockEntity.PROCESSING_TIME;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zurrtum.create.client.catnip.render.CachedBuffers;
import com.zurrtum.create.client.catnip.render.FluidRenderHelper;
import com.zurrtum.create.client.catnip.render.SuperByteBufferRenderState;
import com.zurrtum.create.client.flywheel.lib.model.baked.PartialModel;
import com.zurrtum.create.client.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.zurrtum.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import plus.dragons.createenchantmentindustry.client.model.CEIPartialModels;

/** Printer renderer that snapshots tank and piston animation state during extraction. */
public final class PrinterRenderer
        extends SmartBlockEntityRenderer<PrinterBlockEntity, PrinterRenderer.PrinterRenderState> {
    private static final int PISTON_MOVING_TIME = 5;

    public PrinterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public PrinterRenderState createRenderState() {
        return new PrinterRenderState();
    }

    @Override
    public void extractRenderState(
            PrinterBlockEntity printer,
            PrinterRenderState state,
            float tickProgress,
            Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        super.extractRenderState(printer, state, tickProgress, cameraPos, crumblingOverlay);
        state.fluid = null;
        TankSegment tank = printer.tank.getPrimaryTank();
        FluidStack stack = tank.getRenderedFluid();
        float level = tank.getFluidLevel().getValue(tickProgress);
        if (!stack.isEmpty() && level != 0) {
            level = Math.max(level, 0.175F) * (11 / 16.0F);
            float min = 2.5F / 16.0F;
            float max = min + 11 / 16.0F;
            state.fluid = FluidRenderHelper.extractFluidRenderState(
                    null,
                    null,
                    Minecraft.getInstance().getModelManager().getFluidStateModelSet(),
                    stack.getFluid(),
                    stack.getComponentChanges(),
                    min,
                    min,
                    min,
                    max,
                    min + level,
                    max,
                    state.lightCoords,
                    false,
                    true);
        }

        // Las tres piezas moviles se preparan igual que el SpoutRenderer de Create Fly: cada partial
        // se congela en su propio SuperByteBufferRenderState con iluminacion cardinal, y el
        // desplazamiento queda como un float en el estado. Nada se traslada aqui.
        //
        // La version anterior metia los tres buffers en un CustomGeometryRenderer propio y, dentro de
        // su render(), llamaba pose.translate() sobre el Pose que le pasaba el colector diferido y
        // piston.translate() sobre un buffer de CachedBuffers, que es compartido entre todos los
        // printers. Tambien forzaba solidMovingBlock y no aplicaba iluminacion cardinal. Ese camino es
        // el que dejaba la boquilla despegada y con caras que aparecian y desaparecian segun la camara.
        BlockState blockState = printer.getBlockState();
        CardinalLighting lighting = getCardinalLighting(printer.getLevel());
        state.nozzleTop = extractPart(CEIPartialModels.PRINTER_NOZZLE_TOP, blockState, lighting, state.lightCoords);
        state.nozzleBottom = extractPart(CEIPartialModels.PRINTER_NOZZLE_BOTTOM, blockState, lighting, state.lightCoords);
        state.piston = extractPart(CEIPartialModels.PRINTER_PISTON, blockState, lighting, state.lightCoords);
        state.progress = getProgress(printer.processingTicks - tickProgress);
    }

    private static SuperByteBufferRenderState extractPart(
            PartialModel model, BlockState blockState, CardinalLighting lighting, int light) {
        return CachedBuffers.partial(model, blockState)
                .cardinalLighting(lighting)
                .light(light)
                .extractRenderState();
    }

    @Override
    public void submit(
            PrinterRenderState state,
            PoseStack matrices,
            SubmitNodeCollector queue,
            CameraRenderState cameraState) {
        super.submit(state, matrices, queue, cameraState);
        if (state.fluid != null) {
            state.fluid.submit(matrices, queue);
        }
        if (state.nozzleTop == null || state.nozzleBottom == null || state.piston == null) {
            return;
        }
        // Animacion del mod original (NeoForge 2.5.3): la boquilla se recoge hacia arriba en dos tramos
        // acumulativos de 3/32 mientras el piston baja medio bloque a estampar. El piston va fuera del
        // pushPose de la boquilla, asi que no hereda su desplazamiento. El port a 26.1.2 perdio ese
        // popPose y tampoco movia la pieza de arriba.
        float nozzleStep = 3 * state.progress / 32.0F;
        matrices.pushPose();
        matrices.translate(0, nozzleStep, 0);
        state.nozzleTop.submit(matrices, queue);
        matrices.translate(0, nozzleStep, 0);
        state.nozzleBottom.submit(matrices, queue);
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(0, -state.progress / 2.0F, 0);
        state.piston.submit(matrices, queue);
        matrices.popPose();
    }

    public static float getProgress(float ticks) {
        if (ticks < 0) {
            return 0;
        }
        if (ticks < PISTON_MOVING_TIME) {
            return Mth.lerp(ticks / PISTON_MOVING_TIME, 0, 1);
        }
        if (ticks < PROCESSING_TIME - PISTON_MOVING_TIME) {
            return 1;
        }
        if (ticks < PROCESSING_TIME) {
            return Mth.lerp((PROCESSING_TIME - ticks) / PISTON_MOVING_TIME, 0, 1);
        }
        return 0;
    }

    public static final class PrinterRenderState extends SmartRenderState {
        private @Nullable FluidRenderHelper.FluidRenderState fluid;
        private @Nullable SuperByteBufferRenderState nozzleTop;
        private @Nullable SuperByteBufferRenderState nozzleBottom;
        private @Nullable SuperByteBufferRenderState piston;
        private float progress;
    }
}
