package com.taller.mecanico.ui;

import atlantafx.base.theme.Styles;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class CeldasCarrito {
    private static final int MAXIMO_ABSOLUTO = 9999;

    private CeldasCarrito() {
    }

    public static <T> Callback<TableColumn<T, T>, TableCell<T, T>> cantidad(
            Function<T, IntegerProperty> propiedad, ToIntFunction<T> maximo, Function<T, String> avisoExceso,
            Consumer<String> aviso) {
        return col -> new TableCell<>() {
            private final Button menos = botonIcono(Feather.MINUS);
            private final Button mas = botonIcono(Feather.PLUS);
            private final TextField valor = new TextField();
            private final HBox caja = new HBox(4, menos, valor, mas);
            private T actual;

            {
                caja.setAlignment(Pos.CENTER_LEFT);
                caja.getStyleClass().add("stepper");
                valor.setPrefWidth(52);
                valor.setAlignment(Pos.CENTER);
                Formularios.soloDigitos(valor, 4);
                menos.setOnAction(e -> cambiar(propiedad.apply(actual).get() - 1));
                mas.setOnAction(e -> cambiar(propiedad.apply(actual).get() + 1));
                valor.setOnAction(e -> confirmar());
                valor.focusedProperty().addListener((obs, a, enfocado) -> {
                    if (!enfocado) {
                        confirmar();
                    }
                });
            }

            private int limite() {
                return Math.max(1, Math.min(MAXIMO_ABSOLUTO, maximo.applyAsInt(actual)));
            }

            private void confirmar() {
                if (actual == null) {
                    return;
                }
                String texto = valor.getText();
                cambiar(texto == null || texto.isBlank() ? propiedad.apply(actual).get() : Integer.parseInt(texto));
            }

            private void cambiar(int nueva) {
                int limite = limite();
                if (nueva > limite && limite < MAXIMO_ABSOLUTO) {
                    aviso.accept(avisoExceso.apply(actual));
                }
                int acotada = Math.max(1, Math.min(limite, nueva));
                propiedad.apply(actual).set(acotada);
                refrescar(acotada);
            }

            private void refrescar(int cantidad) {
                valor.setText(String.valueOf(cantidad));
                menos.setDisable(cantidad <= 1);
                mas.setDisable(cantidad >= limite());
            }

            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                actual = vacio ? null : item;
                if (actual == null) {
                    setGraphic(null);
                    return;
                }
                refrescar(propiedad.apply(item).get());
                setGraphic(caja);
            }
        };
    }

    public static <T> Callback<TableColumn<T, T>, TableCell<T, T>> importe(
            Function<T, ObjectProperty<BigDecimal>> propiedad, Consumer<String> aviso) {
        return col -> new TableCell<>() {
            private final TextField valor = new TextField();
            private T actual;

            {
                valor.getStyleClass().add("celda-precio");
                valor.setAlignment(Pos.CENTER_RIGHT);
                Formularios.soloDecimal(valor);
                valor.setOnAction(e -> confirmar());
                valor.focusedProperty().addListener((obs, a, enfocado) -> {
                    if (!enfocado) {
                        confirmar();
                    }
                });
            }

            private void confirmar() {
                if (actual == null) {
                    return;
                }
                try {
                    BigDecimal nuevo = new BigDecimal(valor.getText().trim().replace(",", "."));
                    if (nuevo.signum() <= 0) {
                        throw new NumberFormatException();
                    }
                    propiedad.apply(actual).set(nuevo.setScale(2, RoundingMode.HALF_UP));
                } catch (NumberFormatException e) {
                    aviso.accept("Importe invalido; se mantuvo el anterior.");
                }
                valor.setText(texto(propiedad.apply(actual).get()));
            }

            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                actual = vacio ? null : item;
                if (actual == null) {
                    setGraphic(null);
                    return;
                }
                valor.setText(texto(propiedad.apply(item).get()));
                setGraphic(valor);
            }
        };
    }

    public static <T> Callback<TableColumn<T, T>, TableCell<T, T>> quitar(Consumer<T> alQuitar) {
        return col -> new TableCell<>() {
            private final Button boton = botonIcono(Feather.TRASH_2);

            {
                boton.getStyleClass().add(Styles.DANGER);
                boton.setTooltip(new Tooltip("Quitar"));
                boton.setOnAction(e -> alQuitar.accept(getItem()));
            }

            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                setGraphic(vacio ? null : boton);
            }
        };
    }

    public static <T> Callback<TableColumn<T, T>, TableCell<T, T>> numeroFila() {
        return col -> new TableCell<>() {
            {
                getStyleClass().add("celda-id");
            }

            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                setText(vacio ? null : String.valueOf(getIndex() + 1));
            }
        };
    }

    private static String texto(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Button botonIcono(Feather icono) {
        Button b = new Button(null, new FontIcon(icono));
        b.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, "boton-mini");
        return b;
    }
}
