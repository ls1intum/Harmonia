interface WeeklyBarChartProps {
  data: number[];
}

const WeeklyBarChart = ({ data }: WeeklyBarChartProps) => {
  if (!data || data.length === 0) return null;

  const max = Math.max(...data);

  const getBarColor = (value: number) => {
    if (max === 0) return 'bg-primary/20';
    const ratio = value / max;
    if (ratio >= 0.6) return 'bg-primary/90';
    if (ratio >= 0.25) return 'bg-primary/50';
    return 'bg-primary/20';
  };

  return (
    <div className="pt-2">
      <p className="text-xs text-muted-foreground mb-1.5">Daily Activity</p>
      <div className="flex items-end gap-px h-16">
        {data.map((value, idx) => {
          const heightPercent = max > 0 ? Math.max((value / max) * 100, 2) : 2;
          return (
            <div key={idx} className="flex-1 min-w-0 group relative" style={{ height: '100%' }}>
              <div className="absolute bottom-0 left-0 right-0 flex flex-col items-center justify-end h-full">
                <div
                  className={`w-full rounded-sm ${getBarColor(value)} transition-colors group-hover:ring-1 group-hover:ring-primary/40`}
                  style={{ height: `${heightPercent}%`, minHeight: '1px' }}
                />
              </div>
              <div className="absolute -top-7 left-1/2 -translate-x-1/2 hidden group-hover:block z-10">
                <span className="text-[10px] bg-popover text-popover-foreground border rounded px-1.5 py-0.5 whitespace-nowrap shadow-sm">
                  Day {idx + 1}: {Math.round(value)} LoC
                </span>
              </div>
            </div>
          );
        })}
      </div>
      <div className="flex justify-between mt-1">
        <span className="text-[10px] text-muted-foreground">Day 1</span>
        {data.length > 1 && <span className="text-[10px] text-muted-foreground">Day {data.length}</span>}
      </div>
    </div>
  );
};

export default WeeklyBarChart;
