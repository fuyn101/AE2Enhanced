package mezz.jei.input;

import net.minecraft.item.ItemStack;
import javax.annotation.Nullable;
import java.awt.Rectangle;

public class ClickedIngredient<V> implements IClickedIngredient<V> {
    @Nullable
    public static <V> ClickedIngredient<V> create(V value, @Nullable Rectangle area) {
        throw new RuntimeException("Stub");
    }

    @Override
    public V getValue() { throw new RuntimeException("Stub"); }
    @Override
    public Rectangle getArea() { throw new RuntimeException("Stub"); }
    @Override
    public void setOnClickHandler(IOnClickHandler onClickHandler) { }
    @Override
    public ItemStack getCheatItemStack() { throw new RuntimeException("Stub"); }
    @Override
    public void onClickHandled() { }
}
