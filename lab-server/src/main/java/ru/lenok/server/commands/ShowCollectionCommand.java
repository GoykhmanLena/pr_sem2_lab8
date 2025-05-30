package ru.lenok.server.commands;

import ru.lenok.common.CommandRequest;
import ru.lenok.common.CommandResponse;
import ru.lenok.common.commands.AbstractCommand;
import ru.lenok.common.models.LabWorkWithKey;
import ru.lenok.server.collection.LabWorkService;

import java.util.List;

import static ru.lenok.server.commands.CommandName.show;

public class ShowCollectionCommand extends AbstractCommand {
    LabWorkService labWorkService;

    public ShowCollectionCommand(LabWorkService labWorkService) {
        super(show.getBehavior(), "вывести в стандартный поток вывода все элементы коллекции в строковом представлении");
        this.labWorkService = labWorkService;
    }

    private CommandResponse execute(){
        String answer = labWorkService.getMapAsString();
        List<LabWorkWithKey> list = labWorkService.getLabWorkList();
        return new CommandResponse(answer.length() == 0 ? "ПУСТАЯ КОЛЛЕКЦИЯ" : answer, list);
    }

    @Override
    public CommandResponse execute(CommandRequest req) throws Exception {
        return execute();
    }
}
