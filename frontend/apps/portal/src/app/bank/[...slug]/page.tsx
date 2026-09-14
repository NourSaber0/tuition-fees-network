import { EmptyState } from "@tuition/ui";

export default async function BankComingSoonPage({ params }: { params: Promise<{ slug: string[] }> }) {
  const { slug } = await params;
  return (
    <EmptyState
      title="Not built yet"
      description={`/bank/${slug.join("/")} is a separate ticket (BO-P3..BO-P12) - the sidebar link is real, the screen isn't.`}
    />
  );
}
