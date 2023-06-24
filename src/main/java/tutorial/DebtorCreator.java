package tutorial;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import tutorial.Exceptions.IncorrectMessageException;
import tutorial.Exceptions.NegativeValueException;

import javax.validation.constraints.NotNull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DebtorCreator extends Thread {
    private static final Logger logger = LogManager.getLogger("DCreator");
    private boolean HaveAGrowth; // переменная-флаг, обозначающая, что в задолженности установлен рост
    private final long id; //user id
    private int step = 1; // поле, обозначающее номера шагов в записи задолженности (Название - сумма - цикл и тд.)
    private StringBuilder keyboardText = new StringBuilder("""
            \uD83D\uDD24Название:
            \uD83D\uDCB8Сумма:
            \uD83D\uDD04Цикл роста:
            \uD83D\uDCB9Процент роста: %\s
            ℹ️Описание:\s
            """); //Часто изменяемое, текстовое поле, с текстом для меню
    private final StringBuffer text = new StringBuffer(""); //Синхронизированное текстовое поле, и информацией от пользователя (Получение - запись - очистка)
    private final static Bot bot = Main.myBot;
    private final Debtor debtor; // класс Debtor для сериализация задолженности
    private int KeyboardID; // ID меню
    private Message ExceptionMessage; // ID меню
    private Update currentUpdate = null; //Поле с обновлениями
    private boolean isHaveAPhoto; // переменная-флаг, обозначающая, что меню нужно отправлять с фотографией


    public DebtorCreator(long id) {
        debtor = new Debtor(id);
        this.id = id;
        start();

    }

    public void changeStep(boolean low) { //повышает или понижает step на 1
        if (low) step--;
        else step++;
    }

    public void changeStep(boolean low, int count) { //повышает или понижает step на count
        for (int i = 0; i < count; i++) {
            if (low) step--;
            else step++;
        }
    }

    public void setCurrentUpdate(Update currentUpdate) {
        this.currentUpdate = currentUpdate;
    }

    public void setHaveAGrowth(boolean haveAGrowth) {
        HaveAGrowth = haveAGrowth;
    }

    @Override
    public void run() {
        KeyboardID = bot.sendText(id, keyboardText + "\n\n<b>✍Введите название:</b>").getMessageId();
        while (!isInterrupted()) {
            if (!text.toString().equals("")) { // Получения информации от пользователя
                step++; //увеличение шага
                tapCreatorKeyboard(currentUpdate); //запись информации, изменение меню настройки
                text.delete(0, text.length());//очистка поля с информацией
            }
        }
    }

    public int getStep() {
        return step;
    }


    /*
    В каждом шаге происходит соответсвующее изменение текста и кнопок под главным меню, это происходит благодаря ссылкам editText и editKeyboard.

    После перехода на следующий шаг, первым делом происходит проверка была ли нажата какая-нибудь кнопка(помимо шагов, где это необходимо),
    потому что сразу после проверки идет запись данных с предыдущего шага, а когда пользователь нажимает кнопку "Пропустить", никакие данные не передаются.

    Запись данных, для дальнейшей сериализации объекта Debtor, происходит в методе writeInformation.
     */
    public void tapCreatorKeyboard(Update update) { //метод с процессом изменения меню с настройкой
        String editText = null;                   //ссылки, которые принимают значения на каждом шагу
        InlineKeyboardMarkup editKeyboard = null;//
        switch (step) {
            case 1 -> { // к первому шагу можно будет вернуться
                editText = keyboardText.toString() + "\n\n<b>✍Введите название:</b>";
                editKeyboard = Keyboard.makeKeyboard(Keyboard.clearField);
            }
            case 2 -> {
                if (!update.hasCallbackQuery()) writeInformation(text.toString());
                editText = keyboardText.toString() + "\n\n<b>✍Введите сумму:</b>";
                editKeyboard = Keyboard.makeKeyboard(Keyboard.back, Keyboard.clearField);
            }
            case 3 -> {
                if (!update.hasCallbackQuery()) {
                    if (!writeInformation(text.toString())) return;
                }
                editText = keyboardText.toString() + "\n\n<b>✍Желаете установить рост долга?</b>";
                editKeyboard = Keyboard.makeKeyboard(Keyboard.back, Keyboard.skipCycle, Keyboard.make);
            }
            case 4 -> {
                HaveAGrowth = true; //ставим флажок о настройке роста
                editText = "<b>✍Выберите цикл роста:</b>";
                editKeyboard = Keyboard.makeKeyboard(Keyboard.back, Keyboard.seconds, Keyboard.minutes, Keyboard.hours);
            }
            case 5 -> {
                if (!HaveAGrowth) {     //это условие выполнится, если пользователь нажмет кнопку "Назад" на следующем шаге
                    changeStep(true, 2);//тогда он вернется к выбору установки роста
                    tapCreatorKeyboard(update);//рекурсия метода с правильным шагом
                    return;
                }
                String button = update.getCallbackQuery().getData();
                writeInformation(button);
                editText = "<b>✍Какой будет рост в процентах?\nНапишите число:</b>";
                editKeyboard = Keyboard.makeKeyboard(Keyboard.back);
            }
            case 6 -> {
                if (!update.hasCallbackQuery()) {
                    if (!writeInformation(text.toString())) return;
                }
                editText = keyboardText + "\n\n<b>✍Напишите описание или пропустите, если не желаете...</b>";
                editKeyboard = Keyboard.makeKeyboard(Keyboard.back, Keyboard.skip, Keyboard.clearField);
            }
            case 7 -> {
                if (!update.hasCallbackQuery()) writeInformation(text.toString());
                editText = keyboardText + "\n\n<b>✍Желаете загрузить фото?</b>";
                editKeyboard = Keyboard.makeKeyboard(Keyboard.back, Keyboard.skip, Keyboard.clearField);
            }
            case 8 -> { //на восьмом шаге может измениться способ отправки меню, так как если пользователь загрузит фото, придется в дальнейшем
                // отправлять меню с фотографией, а нынешние классы EditMessageText и EditMessageReplyMarkup для этого не подходят.
                editText = keyboardText + "\n\n<b>✍Сохранить изменения?</b>";
                editKeyboard = Keyboard.makeKeyboard(Keyboard.finish, Keyboard.back);
                if (!update.hasCallbackQuery()) { //если пользователь не пропустил этот шаг
                    writeInformation(""); //запись информации
                    bot.deleteMessage(id, KeyboardID); //удаляем старое меню(которое не могло работать с фотографиями)
                    bot.deleteMessage(id, currentUpdate.getMessage().getMessageId()); //удаляем сообщение пользователя из чата
                    KeyboardID = bot.sendPhoto(id, debtor.getPhoto(), editText, editKeyboard);//отправляем новое меню, с сохранением его ID, для дальнейшего изменения
                    isHaveAPhoto = true; //ставим флажок, что теперь меню будет с помощью EditMessageCaption
                    return;
                }
            }
            case 9 -> {
                bot.sendText(id, "Задолженность создана!");
                System.out.println(debtor);
                return;
            }
        }
        changeMenu(editText, editKeyboard); // метод в котором будет меняться меню с настройками
    }

    public void changeInfo(@NotNull String s, Integer step) { // метод для изменения основного текста, с помощью регулярных выражений
        Pattern pattern = null;
        String temp = null;
        switch (step) {
            case 1 -> {

                temp = "Название: ";
                pattern = Pattern.compile("(Название:.*)");
            }
            case 2 -> {

                temp = "Сумма: ";
                pattern = Pattern.compile("(Сумма:.*)");
            }
            case 3 -> {

                temp = "Цикл роста: ";
                pattern = Pattern.compile("(Цикл роста:.*)");
            }
            case 4 -> {
                temp = "Процент роста: ";
                pattern = Pattern.compile("(Процент роста:.*)(%)");
            }
            case 6 -> {
                temp = "Описание: ";
                pattern = Pattern.compile("(Описание:.*)");
            }
            //этот пункт касается 8 шага(загрузки фото)
            case 7 -> { //в нем, в отличие от всех, будет не изменение фотографии(потому что если пользователь и решит изменить фото, меню будет отправлено заново)
                //этот метод будет использоваться, если пользователь на 8 шаге нажмет "Очистить поле", тогда нужно очистить поле photo у Debtor,
                // заново отправить чисто текстовое сообщение, и убрать флаг isHaveAPhoto.
                logger.info("User: " + bot.getUsername(id) + " set photo:" + s);
                debtor.setPhoto(null);
                isHaveAPhoto = false;
                bot.deleteMessage(id, KeyboardID); //удаляем предыдущее меню с фото
                KeyboardID = bot.sendText(id, keyboardText.toString()).getMessageId(); // отправляем текстовое меню
                return;
            }
        }
        Matcher matcher = pattern.matcher(keyboardText);
        if (matcher.find()) {
            keyboardText = new StringBuilder(keyboardText.toString().replaceAll(matcher.group(1), (temp + s)));
        }
    }

    public void NumericExceptions(Exception e) { //Исключения связанные с воддом числовых данных
        if (e instanceof NegativeValueException) {
            sendException("❗Разрешается только положительные числа.❗");
            logger.error(String.format("User:%-28s %-6s %s", bot.getUsername(id), "INCRT:", "Negative value"));
        } else {
            sendException("❗Неправильный ввод числа.❗");
            logger.error(String.format("User:%-28s %-6s %s", bot.getUsername(id), "INCRT:", "Not numeric input"));
        } //используем метод sendException, чтоб отправить ошибку
    }

    public boolean checkExceptions(Update update) { //проверка исключений
        try {
            logger.debug("CheckingExceptions...");
            currentUpdate = update;
            switch (step) {
                case 3, 4, 8 -> { //это для шагов, в которых нужно только нажимать на кнопки
                    if (!update.hasCallbackQuery()) throw new IncorrectMessageException();
                }
                case 7 -> {//это для шага, где нужно отправить фото или пропустить шаг
                    if (update.hasMessage() && !update.getMessage().hasPhoto()) {
                        if (!update.hasCallbackQuery()) {
                            throw new IncorrectMessageException();
                        }
                    }
                }
                case 1, 6 -> {
                    if (!update.getMessage().hasText()) throw new IncorrectMessageException();
                }
                case 2, 5 -> {
                    try {
                        logger.debug("Numeric Exception");
                        int sum = Integer.parseInt(update.getMessage().getText());//пробуем перевести введенный текст в число
                        if (sum <= 0 || update.getMessage().getText().startsWith("0"))
                            throw new NegativeValueException();//проверяем на логические несостыковки
                        debtor.setDebt(sum);
                    } catch (NumberFormatException | NegativeValueException e) {
                        NumericExceptions(e); //вызываем метод числовых исключений
                        return false;
                    }
                    if (!update.getMessage().hasText() || update.getMessage().getText().contains("\\"))
                        throw new IncorrectMessageException();
                }
            }
        } catch (IncorrectMessageException e) {
            logger.error(String.format("User:%-28s %-6s %s", bot.getUsername(id), "INCRT:", bot.checkMessageContent(update.getMessage())));
            sendException("❗Неправильный ввод данных.❗");
            return false;
        }
        return true;
    }

    public void setText(String text) {
        this.text.replace(0, text.length() - 1, text);
    }

    public void sendException(String text) { //метод для отправки ошибки

        if (currentUpdate.hasMessage())
            bot.deleteMessage(id, currentUpdate.getMessage().getMessageId()); // удаляем сообщение пользователя, которое вызвало ошибку
        if (ExceptionMessage != null) {//проверка есть ли в чате старое сообщение об ошибке
            if (ExceptionMessage.getText().equals(text)) return; //если сообщение тоже, что и было, завершить метод
            EditMessageText newTxt = EditMessageText.builder() //если нет, меняем текст сообщения, для другой ошибки(той которая появилась)
                    .chatId(id)
                    .messageId(ExceptionMessage.getMessageId()).text(text).build();
            try {
                ExceptionMessage = (Message) bot.execute(newTxt); //запускаем изменение
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        } else ExceptionMessage = bot.sendText(id, text); // если старых сообщений нет тогда просто отправляем новое
    }

    private boolean writeInformation(String text) { //метод для записи данных в объект Debtor
        switch (step) {
            case 2 -> {
                logger.info(String.format("User:%-28s %-6s %s", bot.getUsername(id), "NAME->", text));
                debtor.setName(text);//сохранение данных происходит в начале следующего шага
                changeInfo(text, 1);//изменение текста меню
            }
            case 3 -> {
                int sum = Integer.parseInt(text);
                debtor.setDebt(sum);
                logger.info(String.format("User:%-28s %-6s %s", bot.getUsername(id), "DEBT->", text));
                changeInfo(text, 2); //меняем основной текст меню настройки
            }
            case 5 -> {
                switch (text) { //проверяем по названию кнопки, которую нажал пользователь

                    case "seconds" -> {
                        debtor.setGrowthCycle(GrowthCycle.SECOND);
                        changeInfo("Каждую секунду", 3);
                    }
                    case "minutes" -> {
                        debtor.setGrowthCycle(GrowthCycle.MINUTE);
                        changeInfo("Каждую минуту", 3);
                    }
                    case "hours" -> {
                        debtor.setGrowthCycle(GrowthCycle.HOUR);
                        changeInfo("Каждый час", 3);
                    }
                }
                logger.info(String.format("User:%-28s %-7s %s", bot.getUsername(id), "CYCLE->", text));
            }
            case 6 -> { //опять же все как позапрошлом шаге
                int percent = Integer.parseInt(text);
                debtor.setPercent(percent);
                logger.info(String.format("User:%-28s %-6s %s", bot.getUsername(id), "GROWTH->", text));
                changeInfo(text, 4);
            }
            case 7 -> {
                debtor.setDescription(text);
                logger.info(String.format("User:%-28s %-6s %s", bot.getUsername(id), "DESC->", text));
                changeInfo(text, 6);
            }
            case 8 -> { // /в случае с фото, мы сохраняем его в каталог с помощью метода savePhoto, который вернет путь к файлу, а затем передадим этот путь полю photo у Debtor
                String url = bot.savePhoto(currentUpdate.getMessage().getPhoto(), id + debtor.getName());
                logger.info(String.format("User:%-28s %-6s %s", bot.getUsername(id), "PHOTO->", url));
                debtor.setPhoto(url);
            }
        }
        return true;
    }

    /*
    Метод для изменения главного меню настройки.
    В нем представлены 2 варианта изменения: 1 - для меню с фото(EditMessageCaption), и 2 - для просто текстового меню(EditMessageText,EditMessageReplyMarkup).
    Все будет зависеть от флажка isHaveAPhoto.
     */
    public void changeMenu(String editText, InlineKeyboardMarkup editKeyboard) {
        if (ExceptionMessage != null) { //сначала удалим сообщение об ошибке, если такое имеется
            bot.deleteMessage(id, ExceptionMessage.getMessageId());
            ExceptionMessage = null;
        }
        if (isHaveAPhoto) { //вариант изменения меню с фотографией
            EditMessageCaption editMessageCaption = new EditMessageCaption();
            editMessageCaption.setChatId(id);
            editMessageCaption.setMessageId(KeyboardID);
            editMessageCaption.setCaption(editText);
            editMessageCaption.setReplyMarkup(editKeyboard);
            editMessageCaption.setParseMode("HTML");
            try {
                bot.execute(editMessageCaption);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        } else { //вариант изменения текстового меню
            try {
                EditMessageText newTxt = EditMessageText.builder()
                        .chatId(id)
                        .messageId(KeyboardID).text(editText).parseMode("HTML").build();
                EditMessageReplyMarkup newKb = EditMessageReplyMarkup.builder()
                        .chatId(id).messageId(KeyboardID).build();
                newKb.setReplyMarkup(editKeyboard);
                bot.execute(newTxt);
                bot.execute(newKb);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }
        if (currentUpdate.getMessage().hasText()) { //удаляем сообщение отправленное пользователем
            bot.deleteMessage(id, currentUpdate.getMessage().getMessageId());
        }
    }
}