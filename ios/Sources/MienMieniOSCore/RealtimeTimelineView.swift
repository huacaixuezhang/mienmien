import SwiftUI

public struct TurnTimelineItem: Identifiable {
    public let id = UUID()
    public let title: String
    public let content: String
    public let createdAt: Date

    public init(title: String, content: String, createdAt: Date = Date()) {
        self.title = title
        self.content = content
        self.createdAt = createdAt
    }
}

@available(iOS 15.0, *)
public final class RealtimeTimelineViewModel: ObservableObject {
    @Published public private(set) var items: [TurnTimelineItem] = []

    public init() {}

    public func appendTurn(event: RealtimeTurnEvent) {
        let content = event.text.trimmingCharacters(in: .whitespacesAndNewlines)
        items.insert(
            TurnTimelineItem(title: event.type.displayLabel, content: content),
            at: 0
        )
    }
}

@available(iOS 15.0, *)
public struct RealtimeTimelineView: View {
    @ObservedObject private var viewModel: RealtimeTimelineViewModel

    public init(viewModel: RealtimeTimelineViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("回合时间线")
                .font(.headline)
                .padding(.horizontal)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    ForEach(viewModel.items) { item in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(item.title)
                                .font(.subheadline.weight(.semibold))
                            if !item.content.isEmpty {
                                Text(item.content)
                                    .font(.footnote)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                }
                .padding(.horizontal)
                .padding(.bottom)
            }
        }
    }
}
