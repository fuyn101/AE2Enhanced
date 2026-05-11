package mezz.jei.input;

import net.minecraft.item.ItemStack;
import java.awt.Rectangle;

public interface IClickedIngredient<V> {
    V getValue();
    Rectangle getArea();
    void setOnClickHandler(IOnClickHandler onClickHandler);
    ItemStack getCheatItemStack();
    void onClickHandled();

    interface IOnClickHandler {
        void onClick();
    }
}
