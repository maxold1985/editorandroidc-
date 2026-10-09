package com.maxold.editorandroidc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class CommandResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        Bundle result = intent.getBundleExtra(TermuxBridge.RESULT_BUNDLE);
        String title = intent.getStringExtra("label");
        StringBuilder report = new StringBuilder("=== ").append(title == null ? "Termux" : title).append(" ===\n");
        if (result == null) {
            report.append("Nenhum resultado recebido do Termux. Verifique permissao e configuracao.\n");
        } else {
            report.append(result.getString("stdout", ""));
            report.append(result.getString("stderr", ""));
            String failure = result.getString("errmsg", "");
            if (!failure.isEmpty()) report.append("\nErro Termux: ").append(failure);
            report.append("\n[exitCode=").append(result.getInt("exitCode", -999)).append("]");
        }
        String output = report.toString();
        if (output.length() > 100000) output = output.substring(output.length() - 100000);
        context.getSharedPreferences("editorandroidc", Context.MODE_PRIVATE).edit()
            .putString("lastResult", output).apply();
        MainActivity.deliverLog(output);
    }
}
