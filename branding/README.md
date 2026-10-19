# Marca

- `switch-phone-icon.svg`: logo vigente (propuesta **A**): dos celulares y flechas de intercambio; el de la derecha lleva el punto verde de "activo". Azul `#1F6FEB`, verde `#2EB872`.
- `proposals/`: A, B (volante) y C (carro y celulares). B y C se descartaron: B no dice "cambiar" y C pierde claridad a 32 px.

El ícono de Android es adaptable: fondo `@color/ic_launcher_background` y primer plano `drawable/ic_launcher_foreground.xml` (el mismo dibujo de A, escalado a la zona segura de 66 dp), con versión monocromática para íconos temáticos. El splash usa el mismo primer plano sobre el azul (`Theme.SwitchPhone.Starting`, `androidx.core:core-splashscreen`).
