package com.reiko.economy.block.entity;

import com.reiko.economy.core.Money;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared state for both shop types: the item for sale and its price.
 *
 * <p>The template is one "bundle": its count is how many items one purchase gives, so a template of
 * 16 cobblestone with a price of 5.00 sells 16 cobblestone for 5.00 per purchase. The template, price and a
 * few display fields are synced to clients so the shop can show its item and price above the block.
 */
public abstract class AbstractShopBlockEntity extends BlockEntity {
    /** Most bundles one trade may move, to keep a single click from emptying a shop by accident. */
    public static final int MAX_BUNDLES_PER_TRADE = 64;

    protected ItemStack template = ItemStack.EMPTY;
    protected long price;

    protected AbstractShopBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The bundle for sale. Do not modify the returned stack. */
    public ItemStack template() {
        return template;
    }

    /** Price of one bundle in stored units. For admin shops this is the price players pay to buy. */
    public long price() {
        return price;
    }

    /** Lines shown floating above the shop. Called on the client. */
    public abstract List<Component> labelLines();

    protected void setTemplateInternal(ItemStack stack) {
        template = stack.copy();
        markChangedAndSync();
    }

    protected void setPriceInternal(long newPrice) {
        price = newPrice;
        markChangedAndSync();
    }

    protected static boolean validPrice(long value) {
        return value >= 0 && value <= Money.MAX_BALANCE;
    }

    /** Saves the change and sends the new display data to nearby players. */
    protected void markChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeDisplayData(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        template = tag.contains("template") ? ItemStack.parseOptional(registries, tag.getCompound("template")) : ItemStack.EMPTY;
        price = tag.getLong("price");
    }

    /** Data both saved to disk and sent to clients. Subclasses add their display fields. */
    protected void writeDisplayData(CompoundTag tag, HolderLookup.Provider registries) {
        if (!template.isEmpty()) {
            tag.put("template", template.save(registries));
        }
        tag.putLong("price", price);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeDisplayData(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
