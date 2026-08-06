import SwiftUI

struct ReframeOverlayView: View {
    let text: String
    var onDismiss: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("COGNITIVE GUARDIAN REFRAME")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.red)
                Spacer()
                Button(action: { onDismiss?() }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.gray)
                }
            }

            Text(text)
                .font(.subheadline)
                .foregroundColor(.primary)

            Text("Take a mindful breath before continuing.")
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .padding()
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.yellow, lineWidth: 1.5)
        )
        .shadow(radius: 4)
    }
}
