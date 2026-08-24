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
import com.zurrtum.create.client.catnip.render.FluidRenderHelper;
import com.zurrtum.create.client.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.zurrtum.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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

        // La boquilla ya no se dibuja por piezas: vive entera dentro de block/printer/block.json.
        //
        // Los tres partials (nozzle_top, nozzle_bottom, piston) fueron authorizados como partes de un
        // modelo unico y tienen caras omitidas a proposito -- nozzle_top y nozzle_bottom sin `up`, los
        // tres elementos del eje del piston sin `down` -- porque en el modelo armado esas caras quedan
        // tapadas por la pieza vecina. Al dibujarlas como geometria separada en el pase de moving block,
        // mientras el cuerpo va en la malla del chunk en el pase solido, el conjunto se desarma: las
        // caras que deberian taparse entre si no siempre lo hacen y el resultado cambia con el angulo
        // de camara.
        //
        // Cerrarles las caras faltantes no alcanza (probado). Con la geometria dentro del modelo
        // estatico se dibuja todo en un solo pase, como en el icono del item, que siempre se vio bien.
        //
        // El costo es la animacion del piston, que ya no se mueve al imprimir. getProgress queda
        // porque la usa PrinterBlockEntity.
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
    }
}
