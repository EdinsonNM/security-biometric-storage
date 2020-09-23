import 'package:flutter/material.dart';
import 'package:security_storage/security_storage.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _value = 'Unknown';
  final _formKey = GlobalKey<FormState>();
  final keyController = TextEditingController();
  final valueController = TextEditingController();
  final _scaffoldKey = GlobalKey<ScaffoldState>();
  @override
  void initState() {
    super.initState();
    init();
  }

  init() async {
    await SecurityStorage.init(
        androidPromptInfo: AndroidPromptInfo(
            title: "Hola mundo",
            description: "pacífico seguros",
            negativeButton: "Cancelar",
            subtitle: "loguin biometrico"));
  }

  _displaySnackBar(BuildContext context, String message) {
    final snackBar = SnackBar(content: Text(message));
    _scaffoldKey.currentState.showSnackBar(snackBar);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        key: _scaffoldKey,
        appBar: AppBar(
          title: const Text('Plugin Security Storage app'),
        ),
        body: Center(
          child: Container(
            padding: EdgeInsets.all(40),
            child: Column(
              children: [
                Form(
                    key: _formKey,
                    child: Column(children: <Widget>[
                      TextFormField(
                        controller: keyController,
                        validator: (value) {
                          if (value.isEmpty) {
                            return 'Nombre de llave';
                          }
                          return null;
                        },
                      ),
                      TextFormField(
                        controller: valueController,
                        validator: (value) {
                          if (value.isEmpty) {
                            return 'Valor de llave';
                          }
                          return null;
                        },
                      )
                    ])),
                RaisedButton(
                  onPressed: () {
                    if (_formKey.currentState.validate()) {
                      _displaySnackBar(context, 'Guardando data');

                      SecurityStorage.write(
                          keyController.value.text, valueController.value.text);
                    }
                  },
                  child: Text('guardar'),
                ),
                RaisedButton(
                  onPressed: () async {
                    if (_formKey.currentState.validate()) {
                      String value =
                          await SecurityStorage.read(keyController.value.text);
                      _displaySnackBar(context, "El valor es: $value");
                    }
                  },
                  child: Text('Leer'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
