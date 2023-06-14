package tutorial;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

public class Keyboard {
    public final static InlineKeyboardButton back = InlineKeyboardButton.builder().text("Назад").callbackData("back").build();
    public final static InlineKeyboardButton clearField = InlineKeyboardButton.builder().text("Очистить поле").callbackData("clear").build();
    public final static InlineKeyboardButton skip = InlineKeyboardButton.builder().text("Пропустить").callbackData("skip").build();
    public final static InlineKeyboardButton make = InlineKeyboardButton.builder().text("Установить").callbackData("make").build();
    public final static InlineKeyboardButton skipCycle = InlineKeyboardButton.builder().text("Пропустить").callbackData("skipCycle").build();
    public final static InlineKeyboardButton seconds = InlineKeyboardButton.builder().text("По секундам").callbackData("seconds").build();
    public final static InlineKeyboardButton minutes = InlineKeyboardButton.builder().text("По минутам").callbackData("minutes").build();
    public final static InlineKeyboardButton hours = InlineKeyboardButton.builder().text("По часам").callbackData("hours").build();
    public final static InlineKeyboardButton finish = InlineKeyboardButton.builder().text("Сохранить").callbackData("finish").build();

    public static InlineKeyboardMarkup makeKeyboard(InlineKeyboardButton... inlineKeyboardButtons) {
        return InlineKeyboardMarkup.builder().keyboardRow(List.of(inlineKeyboardButtons)).build();

    }
}
