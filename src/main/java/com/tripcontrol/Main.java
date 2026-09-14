package com.tripcontrol;

import com.tripcontrol.app.TripControlApp;

/**
 * Ponto de entrada do TripControl.
 *
 * <p>Delega para {@link TripControlApp} em vez de estender {@code Application}
 * diretamente: isso permite iniciar o sistema por {@code java -jar} mesmo com o
 * JavaFX fora do module path.</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] argumentos) {
        TripControlApp.main(argumentos);
    }
}
