package io.github.emiliasamaemt.bluemaprailway.fabric;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

final class FabricYamlSupport {

    private static final int CODE_POINT_LIMIT = 100 * 1024 * 1024;

    private FabricYamlSupport() {
    }

    static Yaml readerYaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(CODE_POINT_LIMIT);
        return new Yaml(new SafeConstructor(loaderOptions));
    }

    static String errorMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }
}
