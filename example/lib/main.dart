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

  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin Security Storage app'),
        ),
        body: Center(
          child: Column(
            children: [
              Text('Running on: $_value\n'),
              MaterialButton(
                  child: Text("escribir"),
                  onPressed: () async {
                    var result = await SecurityStorage.encrypt();
                    print(result);
                  }),
              MaterialButton(
                  child: Text("leer"),
                  onPressed: () async {
                    var result = await SecurityStorage.decrypt();
                    print(result);
                    setState(() {
                      _value = result;
                    });
                  })
            ],
          ),
        ),
      ),
    );
  }
}
