package ru.lenok.server.request_processing;

import com.google.common.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.lenok.common.CommandRequest;
import ru.lenok.common.CommandResponse;
import ru.lenok.common.CommandWithArgument;
import ru.lenok.common.auth.LoginRequest;
import ru.lenok.common.auth.LoginResponse;
import ru.lenok.common.auth.User;
import ru.lenok.common.commands.CommandBehavior;
import ru.lenok.common.models.LabWork;
import ru.lenok.server.commands.CommandName;
import ru.lenok.server.commands.CommandRegistry;
import ru.lenok.server.commands.IHistoryProvider;
import ru.lenok.server.connectivity.ClientAddress;
import ru.lenok.server.connectivity.IncomingMessage;
import ru.lenok.server.connectivity.ResponseWithClient;
import ru.lenok.server.services.UserService;
import ru.lenok.server.utils.HistoryList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static ru.lenok.common.commands.ArgType.LONG;
import static ru.lenok.server.commands.CommandName.exit;
import static ru.lenok.server.commands.CommandName.save;

public class RequestHandler implements IHistoryProvider {
    private static final int THREAD_COUNT = 5;
    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);
    private final CommandController commandController;
    private final CommandRegistry commandRegistry;
    private final UserController userController;
    private Map<Long, HistoryList> historyByClients = new ConcurrentHashMap();
    private final UserService userService;
    public CommandController getCommandController() {
        return commandController;
    }
    private Cache<ClientAddress, ClientAddress> clientsCache;

    public RequestHandler(CommandRegistry commandRegistry, UserService userService, Cache<ClientAddress, ClientAddress> clientsCache) {
        this.userService = userService;
        this.commandRegistry = commandRegistry;
        this.commandController = new CommandController(commandRegistry);
        this.userController = new UserController(userService, commandRegistry.getClientCommandDefinitions());
        this.clientsCache = clientsCache;
    }

    public ResponseWithClient handleIncomingMessage(IncomingMessage message) {
        Object response = onReceive(message);
        return new ResponseWithClient(response, message.getClientIp(), message.getClientPort());
    }

    public Object onReceive(IncomingMessage message) {
        Object inputData = message.getMessage();
        logger.info("Обрабатываю сообщение: " + inputData);
        if (inputData instanceof CommandRequest) {
            CommandRequest commandRequest = (CommandRequest) inputData;
            CommandResponse validateResponse = validateCommandRequest(commandRequest);
            if (validateResponse != null) {
                return validateResponse;
            }
            String commandNameStr = commandRequest.getCommandWithArgument().getCommandName();
            CommandName commandName = CommandName.valueOf(commandNameStr);
            User user = commandRequest.getUser();
            User userFromDb;
            try {
                userFromDb = userService.login(user);
                commandRequest.setUser(userFromDb);
            } catch (Exception e) {
                return new CommandResponse(e);
            }
            ClientAddress clientAddress = new ClientAddress(message.getClientIp(), commandRequest.getServerNotificationPort());
            clientsCache.put(clientAddress, clientAddress);
            HistoryList historyList = historyByClients.get(userFromDb.getId());
            if (historyList == null) {
                historyList = new HistoryList();
                historyByClients.put(userFromDb.getId(), historyList);
            }
            historyList.addCommand(commandName);
            return commandController.handle(commandRequest);
        } else if (inputData instanceof LoginRequest) {
            LoginRequest loginRequest = (LoginRequest) inputData;
            LoginResponse loginResponse = null;
            if (loginRequest.isRegister()) {
                loginResponse = userController.register(loginRequest.getUser());
            } else {
                loginResponse = userController.login(loginRequest.getUser());
            }
            if (loginResponse.getError() == null) {
                historyByClients.put(loginResponse.getUserId(), new HistoryList());
                ClientAddress clientAddress = new ClientAddress(message.getClientIp(), loginRequest.getServerNotificationPort());
                clientsCache.put(clientAddress, clientAddress);
            }
            return loginResponse;
        }
        return errorResponse("Вы передали какую-то чепуху: ", inputData);
    }

    private static CommandResponse errorResponse(String message, Object inputData) {
        return new CommandResponse(new IllegalArgumentException(message + inputData));
    }

    @Override
    public HistoryList getHistoryByClientID(Long clientID) {
        return historyByClients.get(clientID);
    }

    private CommandResponse validateCommandRequest(CommandRequest commandRequest) {
        LabWork element = commandRequest.getElement();
        CommandWithArgument commandWithArgument = commandRequest.getCommandWithArgument();
        if (commandWithArgument == null) {
            return errorResponse("Неверный формат запроса: ", commandRequest);
        }
        String commandNameStr = commandWithArgument.getCommandName();
        if (commandNameStr == null) {
            return errorResponse("Неверный формат запроса: ", commandRequest);
        }
        CommandName commandName;
        try {
            commandName = CommandName.valueOf(commandNameStr);
        } catch (IllegalArgumentException e) {
            return errorResponse("Такой команды не существует: ", commandRequest);
        }
        if (commandName == save || commandName == exit) {
            return errorResponse("Вы МОШЕННИК: эта команда на сервере не разрешена: ", commandRequest);
        }
        CommandBehavior commandBehavior = commandName.getBehavior();
        String argument1 = commandWithArgument.getArgument1();
        if (commandBehavior.hasArg1()) {
            if (argument1 == null || argument1.isEmpty()) {
                return errorResponse("Ожидался аргумент, ничего не пришло: ", commandRequest);
            }
            if (commandBehavior.getArgType1() == LONG) {
                try {
                    Long.parseLong(argument1);
                } catch (NumberFormatException e) {
                    return errorResponse("Ожидался аргумент типа Long, пришло: ", commandRequest);
                }
            }
        }
        if (commandBehavior.has2Args()) {
            String argument2 = commandWithArgument.getArgument2();
            if (argument2 == null || argument2.isEmpty()) {
                return errorResponse("Ожидалось 2 аргумента, второй аргумент пустой: ", commandRequest);
            }
            if (commandBehavior.getArgType2() == LONG) {
                try {
                    Long.parseLong(argument2);
                } catch (NumberFormatException e) {
                    return errorResponse("Ожидался 2й аргумент типа Long, пришло: ", commandRequest);
                }
            }
        }
        if (commandBehavior.hasElement()) {
            if (element == null) {
                return errorResponse("Ожидался элемент, ничего не пришло: ", commandRequest);
            }
            if (!element.validate()) {
                return errorResponse("Вы передали невалидный элемент: ", commandRequest);
            }
        }
        return null;
    }
}
