package tutorial;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class Bot extends TelegramLongPollingBot {
    private final static Logger logger = LogManager.getLogger("Bot");

    @Override
    public String getBotUsername() {
        return "KolprobBot";
    }

    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN");
    }

    private final HashMap<Long, DebtorCreator> livingCreators = new HashMap<>(); //Мапа с активными нитями(DebtorCreator), для работы с множеством пользователей одновременно

    @Override
    public void onUpdateReceived(Update update) {
        /*
        2 шаг: после создания DebtorCreator, программа будет проверять каждое поступаемое обновление:
         */
        //Под меню с настройкой задолженности, будут появляться кнопки для навигации
        if (update.hasCallbackQuery()) { // если программа словит нажатие на какую-либо кнопку (Пропустить, Назад, Очистить поле)
            if (update.hasCallbackQuery() && update.getCallbackQuery().getFrom() != null) {
                Long ID = update.getCallbackQuery().getFrom().getId();
                if (livingCreators.containsKey(ID)) {
                    CrNavigate(update); //Запуститься процесс реагирование на запрос, и дальнейшего изменения настройки
                    return;
                }
            }
        }
        Long id = update.getMessage().getFrom().getId();
        Message message = update.getMessage();
        User user = update.getMessage().getFrom();
        //Если же пользователь, просто заполняет информацию в ручную,
        if (livingCreators.containsKey(id) && !message.isCommand()) {
            logger.debug("workWithCreator");
            workWithCreator(update); //Программа запишет полученную информацию, в соответствующие поля, и продолжит настройку.
        }
        /*
        1 шаг:
        Для начало работы с ботом нужно вызвать команду /create, после которой пользователю отправиться меню, для настройки задолженности
         */
        else {
            if (update.hasMessage() && update.getMessage().hasText()) {
                logger.info("User: " + user.getUserName());
                if (message.getText().equals("/create")) { //При вызове пользователем команды /create,
                    getCreator(id); // мы создадим для него личный Thread, в котором будет настройка задолженности
                }

            }
        }
    }

    public void workWithCreator(Update update) {
        DebtorCreator debtorCreator = livingCreators.get(update.getMessage().getChatId()); // Получаем ссылку на DebtorCreator пользователя
        if (!debtorCreator.checkExceptions(update)) {
            logger.debug("Exception is detected.");
            return;
        }
        Message message = update.getMessage();
        if (debtorCreator.getStep() == 7 && message.hasPhoto()) { // Перед восьмым шагом, пользователь должен отправить фото, или пропустить шаг.
            // При первом варианте, следует заранее проверить наличие фото,
            //и вручную запустить процесс изменения меню
            debtorCreator.changeStep(false);
            debtorCreator.setCurrentUpdate(update);
            debtorCreator.tapCreatorKeyboard(update);
        }
        debtorCreator.setCurrentUpdate(update); //отправляем ему полученное обновления
        debtorCreator.setText(update.getMessage().getText()); // сохраняем информацию, отправленную пользователем
    }

    private void getCreator(Long id) { //DebtorCreator extends Thread
        if (livingCreators.containsKey(id)) { // Проверяем, существует ли уже поток, для определенного пользователя(в случає повторного вызова /create)
            livingCreators.get(id).interrupt(); //если да, то останавливаем
        }
        livingCreators.put(id, new DebtorCreator(id)); //после создаем новый поток и добавляем в список активных DebtorCreators
    }


    public Message sendText(Long who, String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(who.toString()) //Who are we sending a message to
                .text(what).parseMode("HTML").build();    //Message content
        try {
            return execute(sm);//Actually sending the message
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);      //Any error will be printed here
        }
    }

    private void CrNavigate(Update update) {
        String button = update.getCallbackQuery().getData();
        String username = update.getCallbackQuery().getFrom().getUserName();
        String message = String.format("User:%-28s %-6s \"%s\"", username, "PRESS:", button);
        logger.info(message);
        DebtorCreator debtorCreator = livingCreators.get(update.getCallbackQuery().getFrom().getId());
        AnswerCallbackQuery close = AnswerCallbackQuery.builder()       //Создаем AnswerCallbackQuery чтоб не было вечной загрузки на кнопке
                .callbackQueryId(update.getCallbackQuery().getId()).build();
        try {
            execute(close);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        switch (button) {
            case "back" -> { // Если пользователь хочет вернуться к предыдущему полю,
                debtorCreator.changeStep(true); //делаем шаг назад в его DebtorCreator
                debtorCreator.tapCreatorKeyboard(update); //процесс изменения меню
            }
            case "skip", "make", "seconds", "minutes", "hours", "finish" -> { // Если пользователь не желает заполнить поле
                debtorCreator.changeStep(false);    //пропускаем шаг назад в его DebtorCreator
                debtorCreator.tapCreatorKeyboard(update);//процесс изменения меню

            }
            case "clear" -> { //Для очистки поля
                debtorCreator.changeInfo("", debtorCreator.getStep());
                debtorCreator.tapCreatorKeyboard(update);//процесс изменения меню
            }
            case "skipCycle" -> { // Если пользователь не желает заполнить поле
                debtorCreator.changeStep(false, 3);
                debtorCreator.setHaveAGrowth(false);
                debtorCreator.tapCreatorKeyboard(update);//процесс изменения меню
            }
        }

    }

    public void deleteMessage(Long chatId, int messageID) { //Метод для удаления сообщений
        DeleteMessage deleteMessage = new DeleteMessage(chatId.toString(), messageID);
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    /*
    Метод для сохранения фотографий
    Параметры: список размеров, получаемый методом Message.getPhoto(); и название, под которым сохраниться файл.
    Возвращает: путь к сохраненному файлу
     */
    public String savePhoto(List<PhotoSize> photoSizeList, String name) {
        PhotoSize largestPhoto = photoSizeList.stream().max(Comparator.comparingInt(PhotoSize::getFileSize)).orElse(null); //выбираем фото с лучшим качеством
        GetFile getFile = new GetFile();
        getFile.setFileId(largestPhoto.getFileId());
        try {
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFile); // скачиваем файл
            String url = "https://api.telegram.org/file/bot" + getBotToken() + "/" + file.getFilePath(); // url к фото
            try (InputStream in = new URL(url).openStream()) {
                Path target = Path.of("src/Photos/" + name + ".jpg");
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING); //копируем файл
                return target.toString(); //возвращаем путь
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    /*
        Метод для оправления фотографий
        Параметры:id пользователя(кому отправляем), путь к файлу который хотим отправить; подпись; кнопки.
        Возвращает: ID отправленного сообщения
         */
    public Integer sendPhoto(long id, String URL, String caption, InlineKeyboardMarkup keyboardMarkup) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(id);
        sendPhoto.setPhoto(new InputFile(new java.io.File(URL)));
        sendPhoto.setCaption(caption);
        sendPhoto.setReplyMarkup(keyboardMarkup);
        sendPhoto.setParseMode("HTML");
        try {
            Message response = execute(sendPhoto);
            return response.getMessageId();
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public String getUsername(Long id) {
        GetChat getChat = new GetChat(id.toString());
        try {
            return execute(getChat).getUserName();
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public String checkMessageContent(Message receivedMessage) {
        if (receivedMessage.hasText()) return receivedMessage.getText();
        if (receivedMessage.hasVoice()) return "user send voice.";
        if (receivedMessage.hasVideo()) return "user send video.";
        if (receivedMessage.hasSticker()) return "user send sticker.";
        if (receivedMessage.hasPhoto()) return "user send photo.";
        if (receivedMessage.hasAudio()) return "user send audio.";
        if (receivedMessage.hasAnimation()) return "user send animation.";
        if (receivedMessage.hasVideoNote()) return "user send videoNote.";
        if (receivedMessage.hasLocation()) return "user send location.";
        if (receivedMessage.hasDocument()) return "user send document. ";
        return "user send unknown message";
    }

}